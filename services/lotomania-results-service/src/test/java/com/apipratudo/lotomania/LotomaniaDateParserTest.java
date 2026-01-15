package com.apipratudo.lotomania;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.lotomania.service.LotomaniaDateParser;
import org.junit.jupiter.api.Test;

class LotomaniaDateParserTest {

  @Test
  void normalizaData() {
    assertThat(LotomaniaDateParser.normalize("13/08/2025")).isEqualTo("2025-08-13");
  }
}
