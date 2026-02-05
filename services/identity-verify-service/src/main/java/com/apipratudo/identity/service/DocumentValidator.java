package com.apipratudo.identity.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class DocumentValidator {

  private static final Pattern NON_DIGITS = Pattern.compile("\\D+");

  private DocumentValidator() {
  }

  public static ValidationResult validate(String tipo, String documento) {
    String normalizedType = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
    String normalizedDocument = normalizeDigits(documento);
    return switch (normalizedType) {
      case "CPF" -> validateCpf(normalizedDocument);
      case "CNPJ" -> validateCnpj(normalizedDocument);
      default -> new ValidationResult(normalizedType, normalizedDocument, false, List.of("tipo_desconhecido"));
    };
  }

  public static String normalizeDigits(String value) {
    if (value == null) {
      return "";
    }
    return NON_DIGITS.matcher(value).replaceAll("");
  }

  private static ValidationResult validateCpf(String cpf) {
    List<String> reasons = new ArrayList<>();
    if (cpf.length() != 11) {
      reasons.add("cpf_tamanho_invalido");
      return new ValidationResult("CPF", cpf, false, reasons);
    }
    if (allSameDigits(cpf)) {
      reasons.add("cpf_sequencia_invalida");
      return new ValidationResult("CPF", cpf, false, reasons);
    }

    int firstDigit = calculateCpfDigit(cpf.substring(0, 9), 10);
    int secondDigit = calculateCpfDigit(cpf.substring(0, 9) + firstDigit, 11);

    boolean valid = firstDigit == Character.getNumericValue(cpf.charAt(9))
        && secondDigit == Character.getNumericValue(cpf.charAt(10));

    if (!valid) {
      reasons.add("cpf_dv_invalido");
    }
    return new ValidationResult("CPF", cpf, valid, reasons);
  }

  private static int calculateCpfDigit(String base, int weightStart) {
    int sum = 0;
    for (int i = 0; i < base.length(); i++) {
      int digit = Character.getNumericValue(base.charAt(i));
      sum += digit * (weightStart - i);
    }
    int mod = sum % 11;
    return mod < 2 ? 0 : 11 - mod;
  }

  private static ValidationResult validateCnpj(String cnpj) {
    List<String> reasons = new ArrayList<>();
    if (cnpj.length() != 14) {
      reasons.add("cnpj_tamanho_invalido");
      return new ValidationResult("CNPJ", cnpj, false, reasons);
    }
    if (allSameDigits(cnpj)) {
      reasons.add("cnpj_sequencia_invalida");
      return new ValidationResult("CNPJ", cnpj, false, reasons);
    }

    int first = calculateCnpjDigit(cnpj.substring(0, 12), new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
    int second = calculateCnpjDigit(
        cnpj.substring(0, 12) + first,
        new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}
    );

    boolean valid = first == Character.getNumericValue(cnpj.charAt(12))
        && second == Character.getNumericValue(cnpj.charAt(13));
    if (!valid) {
      reasons.add("cnpj_dv_invalido");
    }
    return new ValidationResult("CNPJ", cnpj, valid, reasons);
  }

  private static int calculateCnpjDigit(String base, int[] weights) {
    int sum = 0;
    for (int i = 0; i < weights.length; i++) {
      int digit = Character.getNumericValue(base.charAt(i));
      sum += digit * weights[i];
    }
    int mod = sum % 11;
    return mod < 2 ? 0 : 11 - mod;
  }

  private static boolean allSameDigits(String value) {
    char first = value.charAt(0);
    for (int i = 1; i < value.length(); i++) {
      if (!Objects.equals(first, value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public record ValidationResult(
      String tipo,
      String documentoNormalizado,
      boolean validoEstrutural,
      List<String> motivos
  ) {
  }
}
