package com.apipratudo.billingsaas.service;

import java.util.UUID;

public final class IdGenerator {

  private IdGenerator() {
  }

  public static String customerId() {
    return "cus_" + randomSuffix();
  }

  public static String chargeId() {
    return "chg_" + randomSuffix();
  }

  private static String randomSuffix() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
