package com.apipratudo.lotofacil;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.lotofacil.service.LotofacilDateParser;
import org.junit.jupiter.api.Test;

class LotofacilDateParserTest {

  @Test
  void normalizaData() {
    assertThat(LotofacilDateParser.normalize("13/08/2025")).isEqualTo("2025-08-13");
  }
}
