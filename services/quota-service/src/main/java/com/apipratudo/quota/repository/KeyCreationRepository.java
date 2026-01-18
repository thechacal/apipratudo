package com.apipratudo.quota.repository;

import java.time.LocalDate;

public interface KeyCreationRepository {

  KeyCreationResult tryReserve(String email, String org, LocalDate day, int maxPerEmail, int maxPerOrg);

  record KeyCreationResult(boolean allowed, int emailCount, int orgCount) {
  }
}
