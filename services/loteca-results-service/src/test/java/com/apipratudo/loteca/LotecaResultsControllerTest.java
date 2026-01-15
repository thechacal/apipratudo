package com.apipratudo.loteca;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apipratudo.loteca.dto.LotecaJogoDTO;
import com.apipratudo.loteca.dto.LotecaResultadoOficialResponse;
import com.apipratudo.loteca.error.ApiExceptionHandler;
import com.apipratudo.loteca.error.UpstreamBadResponseException;
import com.apipratudo.loteca.error.UpstreamTimeoutException;
import com.apipratudo.loteca.service.LotecaResultsService;
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
class LotecaResultsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private LotecaResultsService service;

  @Test
  void retornaResultadoComSucesso() throws Exception {
    LotecaResultadoOficialResponse response = new LotecaResultadoOficialResponse(
        "CAIXA",
        "LOTECA",
        "2760",
        "2025-08-13",
        List.of(
            new LotecaJogoDTO(1, "TIME A", "0", "TIME B", "1"),
            new LotecaJogoDTO(2, "TIME C", "2", "TIME D", "2")
        ),
        Instant.parse("2026-01-15T00:11:55Z")
    );

    given(service.obterResultadoOficial(anyString())).willReturn(response);

    mockMvc.perform(get("/v1/loteca/resultado-oficial"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fonte").value("CAIXA"))
        .andExpect(jsonPath("$.loteria").value("LOTECA"))
        .andExpect(jsonPath("$.concurso").value("2760"))
        .andExpect(jsonPath("$.dataApuracao").value("2025-08-13"))
        .andExpect(jsonPath("$.jogos.length()").value(2))
        .andExpect(jsonPath("$.jogos[0].jogo").value(1))
        .andExpect(jsonPath("$.jogos[0].time1").value("TIME A"))
        .andExpect(jsonPath("$.jogos[0].gols1").value("0"))
        .andExpect(jsonPath("$.jogos[0].time2").value("TIME B"))
        .andExpect(jsonPath("$.jogos[0].gols2").value("1"))
        .andExpect(jsonPath("$.capturadoEm").value("2026-01-15T00:11:55Z"));
  }

  @Test
  void retornaTimeoutComo504() throws Exception {
    given(service.obterResultadoOficial(anyString()))
        .willThrow(new UpstreamTimeoutException("timeout", new RuntimeException("boom")));

    mockMvc.perform(get("/v1/loteca/resultado-oficial"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.error").value("UPSTREAM_TIMEOUT"))
        .andExpect(jsonPath("$.details").isArray());
  }

  @Test
  void retornaBadResponseComo502() throws Exception {
    given(service.obterResultadoOficial(anyString()))
        .willThrow(new UpstreamBadResponseException("bad", List.of("Elemento de resultado nao encontrado")));

    mockMvc.perform(get("/v1/loteca/resultado-oficial"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("UPSTREAM_BAD_RESPONSE"))
        .andExpect(jsonPath("$.details[0]").value("Elemento de resultado nao encontrado"));
  }
}
