package com.apipratudo.federal;

import static org.assertj.core.api.Assertions.assertThat;

import com.apipratudo.federal.service.FederalDateParser;
import org.junit.jupiter.api.Test;

class FederalDateParserTest {

  @Test
  void normalizaDataDdMmParaIso() {
    String normalized = FederalDateParser.normalize("10/01/2026");
    assertThat(normalized).isEqualTo("2026-01-10");
  }
}
