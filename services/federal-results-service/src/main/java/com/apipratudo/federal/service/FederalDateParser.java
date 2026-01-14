package com.apipratudo.federal.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class FederalDateParser {

  private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private FederalDateParser() {
  }

  public static String normalize(String rawDate) {
    if (rawDate == null || rawDate.isBlank()) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(rawDate.trim(), INPUT);
      return date.toString();
    } catch (DateTimeParseException ex) {
      return null;
    }
  }
}
