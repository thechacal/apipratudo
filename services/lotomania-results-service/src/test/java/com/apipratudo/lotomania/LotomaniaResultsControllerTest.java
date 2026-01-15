package com.apipratudo.lotomania;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apipratudo.lotomania.dto.LotomaniaResultadoOficialResponse;
import com.apipratudo.lotomania.error.ApiExceptionHandler;
import com.apipratudo.lotomania.error.UpstreamBadResponseException;
import com.apipratudo.lotomania.error.UpstreamTimeoutException;
import com.apipratudo.lotomania.service.LotomaniaResultsService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(ApiExceptionHandler.class)
class LotomaniaResultsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private LotomaniaResultsService service;

  @Test
  void retornaResultadoComSucesso() throws Exception {
    LotomaniaResultadoOficialResponse response = new LotomaniaResultadoOficialResponse(
        "CAIXA",
        "LOTOMANIA",
        "2760",
        "2025-08-13",
        List.of(
            "00", "01", "02", "03", "04", "05", "06", "07", "08", "09",
            "10", "11", "12", "13", "14", "15", "16", "17", "18", "19"
        ),
        Instant.parse("2026-01-15T00:11:55Z")
    );

    given(service.obterResultadoOficial(anyString())).willReturn(response);

    mockMvc.perform(get("/v1/lotomania/resultado-oficial"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fonte").value("CAIXA"))
        .andExpect(jsonPath("$.loteria").value("LOTOMANIA"))
        .andExpect(jsonPath("$.concurso").value("2760"))
        .andExpect(jsonPath("$.dataApuracao").value("2025-08-13"))
        .andExpect(jsonPath("$.dezenas.length()").value(20))
        .andExpect(jsonPath("$.dezenas[0]").value("00"))
        .andExpect(jsonPath("$.dezenas[19]").value("19"))
        .andExpect(jsonPath("$.capturadoEm").value("2026-01-15T00:11:55Z"));
  }

  @Test
  void retornaTimeoutComo504() throws Exception {
    given(service.obterResultadoOficial(anyString()))
        .willThrow(new UpstreamTimeoutException("timeout", new RuntimeException("boom")));

    mockMvc.perform(get("/v1/lotomania/resultado-oficial"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.error").value("UPSTREAM_TIMEOUT"))
        .andExpect(jsonPath("$.details").isArray());
  }

  @Test
  void retornaBadResponseComo502() throws Exception {
    given(service.obterResultadoOficial(anyString()))
        .willThrow(new UpstreamBadResponseException("bad", List.of("Elemento de resultado nao encontrado")));

    mockMvc.perform(get("/v1/lotomania/resultado-oficial"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("UPSTREAM_BAD_RESPONSE"))
        .andExpect(jsonPath("$.details[0]").value("Elemento de resultado nao encontrado"));
  }
}
