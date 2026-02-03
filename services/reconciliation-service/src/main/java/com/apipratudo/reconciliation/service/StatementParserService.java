package com.apipratudo.reconciliation.service;

import com.apipratudo.reconciliation.error.ApiException;
import com.apipratudo.reconciliation.model.BankTransaction;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StatementParserService {

  public ParsedStatement parse(String tenantId, String importId, String filename, byte[] content) {
    String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    String body = new String(content);
    if (name.endsWith(".ofx") || body.contains("<OFX")) {
      return parseOfx(tenantId, importId, body);
    }
    return parseCsv(tenantId, importId, body);
  }

  private ParsedStatement parseCsv(String tenantId, String importId, String body) {
    try {
      List<BankTransaction> txs = new ArrayList<>();
      BufferedReader reader = new BufferedReader(new StringReader(body));
      String header = reader.readLine();
      if (!StringUtils.hasText(header)) {
        throw new ApiException(400, "INVALID_FILE", "CSV vazio");
      }
      String[] cols = header.split(",");
      int dateIdx = indexOf(cols, "date", "data");
      int amountIdx = indexOf(cols, "amount", "valor");
      int descIdx = indexOf(cols, "description", "descricao", "memo");
      int refIdx = indexOf(cols, "reference", "referencia", "fitid", "id");
      if (dateIdx < 0 || amountIdx < 0) {
        throw new ApiException(400, "INVALID_FILE", "CSV precisa conter date e amount");
      }

      String line;
      while ((line = reader.readLine()) != null) {
        if (!StringUtils.hasText(line)) {
          continue;
        }
        String[] parts = line.split(",", -1);
        LocalDate date = parseDate(value(parts, dateIdx));
        long amountCents = parseAmountCents(value(parts, amountIdx));
        String description = descIdx >= 0 ? value(parts, descIdx) : "";
        String reference = refIdx >= 0 ? value(parts, refIdx) : description;
        String type = amountCents >= 0 ? "CREDIT" : "DEBIT";
        txs.add(new BankTransaction(
            "tx_" + UUID.randomUUID(),
            tenantId,
            importId,
            date,
            Math.abs(amountCents),
            type,
            description,
            reference
        ));
      }
      return summarize("CSV", txs);
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ApiException(400, "INVALID_FILE", "CSV invalido");
    }
  }

  private ParsedStatement parseOfx(String tenantId, String importId, String body) {
    try {
      List<BankTransaction> txs = new ArrayList<>();
      String[] blocks = body.split("<STMTTRN>");
      for (int i = 1; i < blocks.length; i++) {
        String block = blocks[i];
        String dt = extractTag(block, "DTPOSTED");
        String amt = extractTag(block, "TRNAMT");
        String memo = extractTag(block, "MEMO");
        String name = extractTag(block, "NAME");
        String fitid = extractTag(block, "FITID");
        if (!StringUtils.hasText(dt) || !StringUtils.hasText(amt)) {
          continue;
        }
        LocalDate date = parseOfxDate(dt);
        long amountCents = parseAmountCents(amt);
        String description = StringUtils.hasText(memo) ? memo : name;
        String reference = StringUtils.hasText(fitid) ? fitid : description;
        String type = amountCents >= 0 ? "CREDIT" : "DEBIT";
        txs.add(new BankTransaction(
            "tx_" + UUID.randomUUID(),
            tenantId,
            importId,
            date,
            Math.abs(amountCents),
            type,
            description,
            reference
        ));
      }
      if (txs.isEmpty()) {
        throw new ApiException(400, "INVALID_FILE", "OFX sem transacoes");
      }
      return summarize("OFX", txs);
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ApiException(400, "INVALID_FILE", "OFX invalido");
    }
  }

  private ParsedStatement summarize(String format, List<BankTransaction> txs) {
    long credits = 0;
    long debits = 0;
    LocalDate min = null;
    LocalDate max = null;
    for (BankTransaction tx : txs) {
      if ("CREDIT".equals(tx.type())) {
        credits += tx.amountCents();
      } else {
        debits += tx.amountCents();
      }
      if (min == null || tx.date().isBefore(min)) {
        min = tx.date();
      }
      if (max == null || tx.date().isAfter(max)) {
        max = tx.date();
      }
    }
    return new ParsedStatement(format, txs, min, max, credits, debits);
  }

  private int indexOf(String[] cols, String... names) {
    for (int i = 0; i < cols.length; i++) {
      String normalized = cols[i].trim().toLowerCase(Locale.ROOT);
      for (String name : names) {
        if (normalized.equals(name)) {
          return i;
        }
      }
    }
    return -1;
  }

  private String value(String[] parts, int idx) {
    if (idx < 0 || idx >= parts.length) {
      return "";
    }
    return parts[idx].trim();
  }

  private LocalDate parseDate(String value) {
    if (!StringUtils.hasText(value)) {
      throw new ApiException(400, "INVALID_FILE", "Data ausente");
    }
    try {
      if (value.contains("/")) {
        return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
      }
      return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (DateTimeParseException ex) {
      throw new ApiException(400, "INVALID_FILE", "Data invalida: " + value);
    }
  }

  private LocalDate parseOfxDate(String value) {
    String compact = value.trim();
    if (compact.length() < 8) {
      throw new ApiException(400, "INVALID_FILE", "DTPOSTED invalido");
    }
    String yyyyMMdd = compact.substring(0, 8);
    return LocalDate.parse(yyyyMMdd, DateTimeFormatter.ofPattern("yyyyMMdd"));
  }

  private long parseAmountCents(String value) {
    String normalized = value.trim().replace(" ", "");
    if (normalized.contains(",") && !normalized.contains(".")) {
      normalized = normalized.replace(",", ".");
    } else if (normalized.contains(",") && normalized.contains(".")) {
      normalized = normalized.replace(".", "").replace(",", ".");
    }
    BigDecimal decimal = new BigDecimal(normalized);
    return decimal.multiply(BigDecimal.valueOf(100)).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
  }

  private String extractTag(String block, String tag) {
    int idx = block.indexOf("<" + tag + ">");
    if (idx < 0) {
      return null;
    }
    int start = idx + tag.length() + 2;
    int end = block.indexOf('\n', start);
    if (end < 0) {
      end = block.indexOf('<', start);
      if (end < 0) {
        end = block.length();
      }
    }
    return block.substring(start, end).trim();
  }
}
