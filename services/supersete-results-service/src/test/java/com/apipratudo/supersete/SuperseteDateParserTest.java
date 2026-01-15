package com.apipratudo.supersete;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.supersete.service.SuperseteDateParser;
import org.junit.jupiter.api.Test;

class SuperseteDateParserTest {

  @Test
  void normalizaData() {
    assertThat(SuperseteDateParser.normalize("13/08/2025")).isEqualTo("2025-08-13");
  }
}
