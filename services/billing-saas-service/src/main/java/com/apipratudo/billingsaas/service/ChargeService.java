package com.apipratudo.billingsaas.service;

import com.apipratudo.billingsaas.dto.ChargeCreateRequest;
import com.apipratudo.billingsaas.dto.ChargeResponse;
import com.apipratudo.billingsaas.dto.ChargeStatusResponse;
import com.apipratudo.billingsaas.dto.PixDataResponse;
import com.apipratudo.billingsaas.dto.PixGenerateResponse;
import com.apipratudo.billingsaas.dto.PixWebhookRequest;
import com.apipratudo.billingsaas.dto.RecurrenceRequest;
import com.apipratudo.billingsaas.error.ResourceNotFoundException;
import com.apipratudo.billingsaas.idempotency.IdempotencyTransaction;
import com.apipratudo.billingsaas.model.Charge;
import com.apipratudo.billingsaas.model.ChargeStatus;
import com.apipratudo.billingsaas.model.PixData;
import com.apipratudo.billingsaas.model.PixProviderIndex;
import com.apipratudo.billingsaas.model.Recurrence;
import com.apipratudo.billingsaas.model.RecurrenceFrequency;
import com.apipratudo.billingsaas.model.ReportSummary;
import com.apipratudo.billingsaas.provider.PixProvider;
import com.apipratudo.billingsaas.repository.ChargeStore;
import com.apipratudo.billingsaas.repository.CustomerStore;
import com.apipratudo.billingsaas.repository.PixProviderIndexStore;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChargeService {

  private final ChargeStore chargeStore;
  private final CustomerStore customerStore;
  private final PixProvider pixProvider;
  private final PixProviderIndexStore pixProviderIndexStore;
  private final Clock clock;

  public ChargeService(
      ChargeStore chargeStore,
      CustomerStore customerStore,
      PixProvider pixProvider,
      PixProviderIndexStore pixProviderIndexStore,
      Clock clock
  ) {
    this.chargeStore = chargeStore;
    this.customerStore = customerStore;
    this.pixProvider = pixProvider;
    this.pixProviderIndexStore = pixProviderIndexStore;
    this.clock = clock;
  }

  public Charge create(String tenantId, ChargeCreateRequest request, IdempotencyTransaction transaction) {
    customerStore.findById(tenantId, request.getCustomerId())
        .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

    Instant now = Instant.now(clock);
    String currency = StringUtils.hasText(request.getCurrency()) ? request.getCurrency().trim() : "BRL";
    Recurrence recurrence = toRecurrence(request.getRecurrence());

    Charge charge = new Charge(
        IdGenerator.chargeId(),
        request.getCustomerId(),
        request.getAmountCents(),
        currency,
        request.getDescription(),
        request.getDueDate(),
        recurrence,
        normalizeMetadata(request.getMetadata()),
        ChargeStatus.CREATED,
        now,
        now,
        null,
        null,
        null,
        tenantId
    );

    return chargeStore.save(tenantId, charge, transaction);
  }

  public Charge get(String tenantId, String id) {
    Optional<Charge> existing = chargeStore.findById(tenantId, id);
    return existing.orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
  }

  public PixGenerateResponse generatePix(
      String tenantId,
      String chargeId,
      Long expiresInSeconds,
      IdempotencyTransaction transaction
  ) {
    Charge charge = get(tenantId, chargeId);
    PixData pixData = charge.pix();
    if (pixData == null) {
      long ttl = expiresInSeconds == null ? 3600 : expiresInSeconds;
      pixData = pixProvider.generatePix(charge, ttl);
      Instant now = Instant.now(clock);
      charge = new Charge(
          charge.id(),
          charge.customerId(),
          charge.amountCents(),
          charge.currency(),
          charge.description(),
          charge.dueDate(),
          charge.recurrence(),
          charge.metadata(),
          ChargeStatus.PIX_GENERATED,
          charge.createdAt(),
          now,
          charge.paidAt(),
          pixData,
          pixData.providerChargeId(),
          charge.tenantId()
      );
      chargeStore.save(tenantId, charge, transaction);
    }
    persistProviderIndex(tenantId, charge.id(), pixData, transaction);

    PixGenerateResponse response = new PixGenerateResponse();
    response.setChargeId(charge.id());
    response.setStatus(charge.status());
    response.setPix(toPixResponse(pixData));
    response.setWhatsappLink(buildWhatsappLink(pixData.pixCopyPaste()));
    return response;
  }

  public Charge handleWebhookPaid(PixWebhookRequest request) {
    if (!pixProvider.providerName().equalsIgnoreCase(request.getProvider())) {
      throw new IllegalArgumentException("Unsupported provider");
    }
    Charge charge = resolveChargeByProviderId(request.getProviderChargeId())
        .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));

    if (charge.status() == ChargeStatus.CANCELED || charge.status() == ChargeStatus.EXPIRED) {
      throw new IllegalArgumentException("Charge not eligible for payment");
    }

    if (charge.status() == ChargeStatus.PAID) {
      return charge;
    }

    Instant paidAt = request.getPaidAt() == null ? Instant.now(clock) : request.getPaidAt();
    Instant now = Instant.now(clock);
    Charge updated = new Charge(
        charge.id(),
        charge.customerId(),
        charge.amountCents(),
        charge.currency(),
        charge.description(),
        charge.dueDate(),
        charge.recurrence(),
        charge.metadata(),
        ChargeStatus.PAID,
        charge.createdAt(),
        now,
        paidAt,
        charge.pix(),
        charge.providerChargeId(),
        charge.tenantId()
    );

    String tenantId = charge.tenantId();
    if (!StringUtils.hasText(tenantId)) {
      throw new IllegalStateException("Missing tenantId for charge");
    }

    chargeStore.save(tenantId, updated);

    if (updated.recurrence() != null && updated.dueDate() != null) {
      Charge nextCharge = buildNextRecurringCharge(updated);
      chargeStore.save(tenantId, nextCharge);
    }

    return updated;
  }

  public ReportSummary reportSummary(String tenantId, LocalDate from, LocalDate to) {
    Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = to.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();
    List<Charge> charges = chargeStore.findByCreatedAtBetween(tenantId, start, end);

    long countTotal = charges.size();
    long countPaid = 0;
    long countPending = 0;
    long totalCents = 0;
    long paidCents = 0;
    long pendingCents = 0;

    for (Charge charge : charges) {
      totalCents += charge.amountCents();
      if (charge.status() == ChargeStatus.PAID) {
        countPaid++;
        paidCents += charge.amountCents();
      } else {
        countPending++;
        pendingCents += charge.amountCents();
      }
    }

    return new ReportSummary(from, to, countTotal, countPaid, countPending, totalCents, paidCents, pendingCents);
  }

  public ChargeResponse toResponse(Charge charge) {
    ChargeResponse response = new ChargeResponse();
    response.setId(charge.id());
    response.setCustomerId(charge.customerId());
    response.setAmountCents(charge.amountCents());
    response.setCurrency(charge.currency());
    response.setDescription(charge.description());
    response.setDueDate(charge.dueDate());
    response.setRecurrence(toRecurrenceRequest(charge.recurrence()));
    response.setMetadata(charge.metadata());
    response.setStatus(charge.status());
    response.setCreatedAt(charge.createdAt());
    response.setUpdatedAt(charge.updatedAt());
    response.setPaidAt(charge.paidAt());
    response.setPix(toPixResponse(charge.pix()));
    return response;
  }

  public ChargeStatusResponse toStatusResponse(Charge charge) {
    ChargeStatusResponse response = new ChargeStatusResponse();
    response.setId(charge.id());
    response.setStatus(charge.status());
    response.setPaidAt(charge.paidAt());
    response.setPix(toPixResponse(charge.pix()));
    return response;
  }

  private PixDataResponse toPixResponse(PixData pixData) {
    if (pixData == null) {
      return null;
    }
    PixDataResponse response = new PixDataResponse();
    response.setProvider(pixData.provider());
    response.setProviderChargeId(pixData.providerChargeId());
    response.setTxid(pixData.txid());
    response.setPixCopyPaste(pixData.pixCopyPaste());
    response.setQrCodeBase64(pixData.qrCodeBase64());
    response.setExpiresAt(pixData.expiresAt());
    return response;
  }

  private void persistProviderIndex(
      String tenantId,
      String chargeId,
      PixData pixData,
      IdempotencyTransaction transaction
  ) {
    if (pixData == null || pixData.providerChargeId() == null) {
      return;
    }
    PixProviderIndex index = new PixProviderIndex(
        pixData.providerChargeId(),
        tenantId,
        chargeId,
        Instant.now(clock)
    );
    pixProviderIndexStore.save(index, transaction);
  }

  private Optional<Charge> resolveChargeByProviderId(String providerChargeId) {
    Optional<PixProviderIndex> index = pixProviderIndexStore.findByProviderChargeId(providerChargeId);
    if (index.isPresent()) {
      PixProviderIndex resolved = index.get();
      return chargeStore.findById(resolved.tenantId(), resolved.chargeId());
    }
    return chargeStore.findByProviderChargeId(providerChargeId);
  }

  private Charge buildNextRecurringCharge(Charge paidCharge) {
    Recurrence recurrence = paidCharge.recurrence();
    LocalDate dueDate = paidCharge.dueDate();
    LocalDate nextDueDate = calculateNextDueDate(dueDate, recurrence);
    Instant now = Instant.now(clock);

    return new Charge(
        IdGenerator.chargeId(),
        paidCharge.customerId(),
        paidCharge.amountCents(),
        paidCharge.currency(),
        paidCharge.description(),
        nextDueDate,
        recurrence,
        paidCharge.metadata(),
        ChargeStatus.CREATED,
        now,
        now,
        null,
        null,
        null,
        paidCharge.tenantId()
    );
  }

  private LocalDate calculateNextDueDate(LocalDate current, Recurrence recurrence) {
    int interval = recurrence.interval() < 1 ? 1 : recurrence.interval();
    LocalDate base = current == null ? LocalDate.now(clock) : current;
    LocalDate next = base.plusMonths(interval);
    int targetDay = recurrence.dayOfMonth() != null ? recurrence.dayOfMonth() : base.getDayOfMonth();
    int lastDay = next.lengthOfMonth();
    if (targetDay > lastDay) {
      targetDay = lastDay;
    }
    return LocalDate.of(next.getYear(), next.getMonth(), targetDay);
  }

  private String buildWhatsappLink(String pixCopyPaste) {
    if (!StringUtils.hasText(pixCopyPaste)) {
      return null;
    }
    String message = "Pix para pagamento: " + pixCopyPaste;
    String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
    return "https://wa.me/?text=" + encoded;
  }

  private Recurrence toRecurrence(RecurrenceRequest request) {
    if (request == null) {
      return null;
    }
    RecurrenceFrequency frequency = request.getFrequency();
    if (frequency == null) {
      return null;
    }
    int interval = request.getInterval() == null ? 1 : request.getInterval();
    return new Recurrence(frequency, interval, request.getDayOfMonth());
  }

  private RecurrenceRequest toRecurrenceRequest(Recurrence recurrence) {
    if (recurrence == null) {
      return null;
    }
    RecurrenceRequest request = new RecurrenceRequest();
    request.setFrequency(recurrence.frequency());
    request.setInterval(recurrence.interval());
    request.setDayOfMonth(recurrence.dayOfMonth());
    return request;
  }

  private Map<String, String> normalizeMetadata(Map<String, String> metadata) {
    if (metadata == null) {
      return Map.of();
    }
    return metadata;
  }
}
