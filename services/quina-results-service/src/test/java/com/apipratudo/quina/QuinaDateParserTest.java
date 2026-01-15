package com.apipratudo.quina;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.quina.service.QuinaDateParser;
import org.junit.jupiter.api.Test;

class QuinaDateParserTest {

  @Test
  void normalizaData() {
    assertThat(QuinaDateParser.normalize("13/08/2025")).isEqualTo("2025-08-13");
  }
}
