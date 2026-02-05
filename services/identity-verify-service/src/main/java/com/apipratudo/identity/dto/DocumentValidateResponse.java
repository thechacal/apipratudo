package com.apipratudo.identity.dto;

import java.util.List;

public record DocumentValidateResponse(
    String tipo,
    String documento_normalizado,
    boolean valido_estrutural,
    List<String> motivos
) {
}
