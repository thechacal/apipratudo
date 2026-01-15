package com.apipratudo.diadesorte;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.diadesorte.service.DiadesorteDateParser;
import org.junit.jupiter.api.Test;

class DiadesorteDateParserTest {

  @Test
  void normalizaData() {
    assertThat(DiadesorteDateParser.normalize("13/08/2025")).isEqualTo("2025-08-13");
  }
}
