package com.apipratudo.duplasena;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.duplasena.service.DuplasenaDateParser;
import org.junit.jupiter.api.Test;

class DuplasenaDateParserTest {

  @Test
  void normalizaData() {
    assertThat(DuplasenaDateParser.normalize("13/08/2025")).isEqualTo("2025-08-13");
  }
}
