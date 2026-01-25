package com.apipratudo.billingsaas.provider;

import com.apipratudo.billingsaas.model.Charge;
import com.apipratudo.billingsaas.model.PixData;

public interface PixProvider {
  PixData generatePix(Charge charge, long expiresInSeconds);
  String providerName();
}
