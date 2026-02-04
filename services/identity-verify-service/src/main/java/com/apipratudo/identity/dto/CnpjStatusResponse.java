package com.apipratudo.identity.dto;

import java.time.Instant;

public record CnpjStatusResponse(
    String cnpj,
    boolean valido_estrutural,
    String status,
    String razao_social,
    String fonte,
    Instant consultadoEm
) {
}
