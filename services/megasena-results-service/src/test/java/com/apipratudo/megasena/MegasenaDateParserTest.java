package com.apipratudo.megasena;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.megasena.service.MegasenaDateParser;
import org.junit.jupiter.api.Test;

class MegasenaDateParserTest {

  @Test
  void normalizaData() {
    assertThat(MegasenaDateParser.normalize("13/08/2025")).isEqualTo("2025-08-13");
  }
}
