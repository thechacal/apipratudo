package com.apipratudo.portal.repository;

import java.time.LocalDate;

public interface KeyRequestRepository {

  KeyRequestResult tryReserve(String email, String org, LocalDate day, int maxPerEmail, int maxPerOrg);

  record KeyRequestResult(boolean allowed, int emailCount, int orgCount) {
  }
}
