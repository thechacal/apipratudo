package com.apipratudo.timemania;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.timemania.service.TimemaniaDateParser;
import org.junit.jupiter.api.Test;

class TimemaniaDateParserTest {

  @Test
  void normalizaData() {
    assertThat(TimemaniaDateParser.normalize("13/08/2025")).isEqualTo("2025-08-13");
  }
}
