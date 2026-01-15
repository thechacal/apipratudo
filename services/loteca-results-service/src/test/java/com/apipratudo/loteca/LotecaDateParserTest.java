package com.apipratudo.loteca;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.loteca.service.LotecaDateParser;
import org.junit.jupiter.api.Test;

class LotecaDateParserTest {

  @Test
  void normalizaData() {
    assertThat(LotecaDateParser.normalize("13/08/2025")).isEqualTo("2025-08-13");
  }
}
