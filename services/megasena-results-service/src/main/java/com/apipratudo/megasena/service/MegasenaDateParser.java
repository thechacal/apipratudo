package com.apipratudo.megasena.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class MegasenaDateParser {

  private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private MegasenaDateParser() {
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
