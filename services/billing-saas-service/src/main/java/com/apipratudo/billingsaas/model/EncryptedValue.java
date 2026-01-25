package com.apipratudo.billingsaas.model;

public record EncryptedValue(
    String cipherTextBase64,
    String ivBase64,
    int version
) {
}
