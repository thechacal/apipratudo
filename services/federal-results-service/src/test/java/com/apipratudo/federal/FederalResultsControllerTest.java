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
            new PremioDTO("1º", "12345", "LOTERICA A", "SAO PAULO/SP", "R$ 500.000,00"),
            new PremioDTO("2º", "54321", "LOTERICA B", "RIO DE JANEIRO/RJ", "R$ 50.000,00"),
            new PremioDTO("3º", "11111", "LOTERICA C", "BELO HORIZONTE/MG", "R$ 10.000,00"),
            new PremioDTO("4º", "22222", "LOTERICA D", "CURITIBA/PR", "R$ 5.000,00"),
            new PremioDTO("5º", "33333", "LOTERICA E", "PORTO ALEGRE/RS", "R$ 2.000,00")
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
        .andExpect(jsonPath("$.premios.length()").value(5))
        .andExpect(jsonPath("$.premios[0].destino").value("1º"))
        .andExpect(jsonPath("$.premios[0].bilhete").value("12345"))
        .andExpect(jsonPath("$.premios[0].unidadeLoterica").value("LOTERICA A"))
        .andExpect(jsonPath("$.premios[0].cidadeUf").value("SAO PAULO/SP"))
        .andExpect(jsonPath("$.premios[0].valorPremio").value("R$ 500.000,00"))
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
