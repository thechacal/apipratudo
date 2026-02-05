package com.apipratudo.gateway.identity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerificationRequest(
    @NotBlank String contexto,
    @NotNull @Valid Documento documento,
    @Valid AtributosOpcionais atributos_opcionais
) {
  public record Documento(
      @NotBlank String tipo,
      @NotBlank String valor
  ) {
  }

  public record AtributosOpcionais(
      String nome,
      String nascimento,
      String email,
      String telefone
  ) {
  }
}
