package com.apipratudo.federal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apipratudo.federal.dto.PremioDTO;
import com.apipratudo.federal.dto.ResultadoOficialResponse;
import com.apipratudo.federal.error.UpstreamBadResponseException;
import com.apipratudo.federal.error.UpstreamTimeoutException;
import com.apipratudo.federal.service.FederalResultsService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import com.apipratudo.federal.error.ApiExceptionHandler;

@WebMvcTest
@Import(ApiExceptionHandler.class)
class FederalResultsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private FederalResultsService service;

  @Test
  void retornaResultadoComSucesso() throws Exception {
    ResultadoOficialResponse response = new ResultadoOficialResponse(
        "CAIXA",
        "FEDERAL",
        "5970",
        "2026-01-10",
        List.of(
            new PremioDTO(1, "12345", null),
            new PremioDTO(2, "54321", null),
            new PremioDTO(3, "11111", null),
            new PremioDTO(4, "22222", null),
            new PremioDTO(5, "33333", null)
        ),
        Instant.parse("2026-01-14T20:30:00Z")
    );

    given(service.obterResultadoOficial(anyString())).willReturn(response);

    mockMvc.perform(get("/v1/federal/resultado-oficial"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fonte").value("CAIXA"))
        .andExpect(jsonPath("$.loteria").value("FEDERAL"))
        .andExpect(jsonPath("$.concurso").value("5970"))
        .andExpect(jsonPath("$.dataApuracao").value("2026-01-10"))
        .andExpect(jsonPath("$.premios[0].posicao").value(1))
        .andExpect(jsonPath("$.premios[0].bilhete").value("12345"))
        .andExpect(jsonPath("$.capturadoEm").value("2026-01-14T20:30:00Z"));
  }

  @Test
  void retornaTimeoutComo504() throws Exception {
    given(service.obterResultadoOficial(anyString()))
        .willThrow(new UpstreamTimeoutException("timeout", new RuntimeException("boom")));

    mockMvc.perform(get("/v1/federal/resultado-oficial"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.error").value("UPSTREAM_TIMEOUT"))
        .andExpect(jsonPath("$.details").isArray());
  }

  @Test
  void retornaBadResponseComo502() throws Exception {
    given(service.obterResultadoOficial(anyString()))
        .willThrow(new UpstreamBadResponseException("bad", List.of("Elemento de resultado nao encontrado")));

    mockMvc.perform(get("/v1/federal/resultado-oficial"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("UPSTREAM_BAD_RESPONSE"))
        .andExpect(jsonPath("$.details[0]").value("Elemento de resultado nao encontrado"));
  }
}
