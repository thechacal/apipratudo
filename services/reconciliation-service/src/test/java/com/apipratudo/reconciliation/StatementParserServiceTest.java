package com.apipratudo.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.reconciliation.service.ParsedStatement;
import com.apipratudo.reconciliation.service.StatementParserService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class StatementParserServiceTest {

  private final StatementParserService parserService = new StatementParserService();

  @Test
  void parseCsvWorks() {
    String csv = "date,amount,description,reference\n2026-02-01,100.00,Pix recebido,ev-123\n2026-02-01,-50.00,Tarifa,db-001\n";
    ParsedStatement parsed = parserService.parse("tenant-1", "imp-1", "extrato.csv",
        csv.getBytes(StandardCharsets.UTF_8));

    assertThat(parsed.format()).isEqualTo("CSV");
    assertThat(parsed.transactions()).hasSize(2);
    assertThat(parsed.totalCreditsCents()).isEqualTo(10000);
    assertThat(parsed.totalDebitsCents()).isEqualTo(5000);
  }

  @Test
  void parseOfxWorks() throws Exception {
    byte[] content = new ClassPathResource("sample.ofx").getInputStream().readAllBytes();
    ParsedStatement parsed = parserService.parse("tenant-1", "imp-1", "extrato.ofx", content);

    assertThat(parsed.format()).isEqualTo("OFX");
    assertThat(parsed.transactions()).hasSize(2);
    assertThat(parsed.totalCreditsCents()).isEqualTo(10000);
    assertThat(parsed.totalDebitsCents()).isEqualTo(5000);
  }
}
