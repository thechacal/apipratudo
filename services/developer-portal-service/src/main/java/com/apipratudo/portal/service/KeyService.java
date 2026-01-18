package com.apipratudo.portal.service;

import com.apipratudo.portal.client.BillingClient;
import com.apipratudo.portal.client.QuotaClient;
import com.apipratudo.portal.config.BillingClientProperties;
import com.apipratudo.portal.config.GatewayProperties;
import com.apipratudo.portal.dto.BillingChargeRequest;
import com.apipratudo.portal.dto.CreateFreeKeyRequest;
import com.apipratudo.portal.dto.CreateFreeKeyResponse;
import com.apipratudo.portal.dto.KeyRequest;
import com.apipratudo.portal.dto.KeyRequestResponse;
import com.apipratudo.portal.dto.KeyUpgradeRequest;
import com.apipratudo.portal.dto.Plan;
import com.apipratudo.portal.rate.IpRateLimiter;
import com.apipratudo.portal.rate.KeyRequestLimiter;
import org.springframework.stereotype.Service;

@Service
public class KeyService {

  private final QuotaClient quotaClient;
  private final BillingClient billingClient;
  private final BillingClientProperties billingProperties;
  private final GatewayProperties gatewayProperties;
  private final IpRateLimiter ipRateLimiter;
  private final KeyRequestLimiter keyRequestLimiter;

  public KeyService(
      QuotaClient quotaClient,
      BillingClient billingClient,
      BillingClientProperties billingProperties,
      GatewayProperties gatewayProperties,
      IpRateLimiter ipRateLimiter,
      KeyRequestLimiter keyRequestLimiter
  ) {
    this.quotaClient = quotaClient;
    this.billingClient = billingClient;
    this.billingProperties = billingProperties;
    this.gatewayProperties = gatewayProperties;
    this.ipRateLimiter = ipRateLimiter;
    this.keyRequestLimiter = keyRequestLimiter;
  }

  public KeyRequestResponse requestKey(KeyRequest request, String ip, String traceId) {
    ipRateLimiter.assertAllowed(ip);
    keyRequestLimiter.assertAllowed(request.email(), request.org());

    CreateFreeKeyResponse created = quotaClient.createFreeKey(
        new CreateFreeKeyRequest(request.email(), request.org(), request.useCase()),
        traceId
    );

    return new KeyRequestResponse(
        created.apiKey(),
        created.plan(),
        created.limits(),
        gatewayProperties.getDocsUrl(),
        gatewayProperties.getUrl()
    );
  }

  public QuotaClient.ClientResult status(String apiKey, String traceId) {
    return quotaClient.status(apiKey, traceId);
  }

  public BillingClient.ClientResult upgrade(String apiKey, KeyUpgradeRequest request, String traceId) {
    Plan plan = Plan.valueOf(request.plan().trim().toUpperCase());
    if (plan != Plan.PREMIUM) {
      throw new IllegalArgumentException("Only PREMIUM plan is supported");
    }

    BillingChargeRequest chargeRequest = new BillingChargeRequest(
        apiKey,
        null,
        plan.name(),
        billingProperties.getPremiumPriceCents(),
        "Plano PREMIUM apipratudo",
        null
    );

    return billingClient.createCharge(chargeRequest, traceId);
  }

  public BillingClient.ClientResult upgradeStatus(String chargeId, String traceId) {
    return billingClient.chargeStatus(chargeId, traceId);
  }
}
