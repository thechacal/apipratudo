package com.apipratudo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentValidatorTest {

  @Test
  void cpfValidoPassa() {
    DocumentValidator.ValidationResult result = DocumentValidator.validate("CPF", "529.982.247-25");
    assertThat(result.validoEstrutural()).isTrue();
    assertThat(result.documentoNormalizado()).isEqualTo("52998224725");
  }

  @Test
  void cpfInvalidoPorDvFalha() {
    DocumentValidator.ValidationResult result = DocumentValidator.validate("CPF", "529.982.247-24");
    assertThat(result.validoEstrutural()).isFalse();
    assertThat(result.motivos()).contains("cpf_dv_invalido");
  }

  @Test
  void cpfSequenciaFalha() {
    DocumentValidator.ValidationResult result = DocumentValidator.validate("CPF", "11111111111");
    assertThat(result.validoEstrutural()).isFalse();
    assertThat(result.motivos()).contains("cpf_sequencia_invalida");
  }

  @Test
  void cnpjValidoPassa() {
    DocumentValidator.ValidationResult result = DocumentValidator.validate("CNPJ", "04.252.011/0001-10");
    assertThat(result.validoEstrutural()).isTrue();
    assertThat(result.documentoNormalizado()).isEqualTo("04252011000110");
  }

  @Test
  void cnpjInvalidoFalha() {
    DocumentValidator.ValidationResult result = DocumentValidator.validate("CNPJ", "04.252.011/0001-11");
    assertThat(result.validoEstrutural()).isFalse();
    assertThat(result.motivos()).contains("cnpj_dv_invalido");
  }
}
