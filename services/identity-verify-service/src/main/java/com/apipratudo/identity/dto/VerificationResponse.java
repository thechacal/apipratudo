package com.apipratudo.identity.dto;

import java.util.List;

public record VerificationResponse(
    String veredito,
    Sinais sinais,
    List<Risco> riscos,
    List<String> proximos_passos
) {
  public record Sinais(
      boolean valido_estrutural,
      String tipo,
      String cnpj_status,
      boolean atributos_fornecidos
  ) {
  }

  public record Risco(
      String regra,
      int peso,
      boolean hit
  ) {
  }
}
