package com.apipratudo.billingsaas.provider;

import com.apipratudo.billingsaas.model.Charge;
import com.apipratudo.billingsaas.model.Customer;
import com.apipratudo.billingsaas.model.PixData;

public interface PixProvider {
  PixData generatePix(String tenantId, Charge charge, Customer customer, long expiresInSeconds);
  String providerName();
}
