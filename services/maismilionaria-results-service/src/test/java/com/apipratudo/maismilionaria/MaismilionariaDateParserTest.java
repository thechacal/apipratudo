package com.apipratudo.maismilionaria;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.maismilionaria.service.MaismilionariaDateParser;
import org.junit.jupiter.api.Test;

class MaismilionariaDateParserTest {

  @Test
  void normalizaData() {
    assertThat(MaismilionariaDateParser.normalize("13/08/2025")).isEqualTo("2025-08-13");
  }
}
