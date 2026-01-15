package com.apipratudo.maismilionaria;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apipratudo.maismilionaria.dto.MaismilionariaResultadoOficialResponse;
import com.apipratudo.maismilionaria.error.ApiExceptionHandler;
import com.apipratudo.maismilionaria.error.UpstreamBadResponseException;
import com.apipratudo.maismilionaria.error.UpstreamTimeoutException;
import com.apipratudo.maismilionaria.service.MaismilionariaResultsService;
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
class MaismilionariaResultsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private MaismilionariaResultsService service;

  @Test
  void retornaResultadoComSucesso() throws Exception {
    MaismilionariaResultadoOficialResponse response = new MaismilionariaResultadoOficialResponse(
        "CAIXA",
        "MAISMILIONARIA",
        "2760",
        "2025-08-13",
        List.of("01", "03", "04", "05", "07", "08"),
        List.of("01", "02"),
        Instant.parse("2026-01-15T00:11:55Z")
    );

    given(service.obterResultadoOficial(anyString())).willReturn(response);

    mockMvc.perform(get("/v1/maismilionaria/resultado-oficial"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fonte").value("CAIXA"))
        .andExpect(jsonPath("$.loteria").value("MAISMILIONARIA"))
        .andExpect(jsonPath("$.concurso").value("2760"))
        .andExpect(jsonPath("$.dataApuracao").value("2025-08-13"))
        .andExpect(jsonPath("$.dezenas.length()").value(6))
        .andExpect(jsonPath("$.dezenas[0]").value("01"))
        .andExpect(jsonPath("$.dezenas[5]").value("08"))
        .andExpect(jsonPath("$.trevos.length()").value(2))
        .andExpect(jsonPath("$.trevos[0]").value("01"))
        .andExpect(jsonPath("$.trevos[1]").value("02"))
        .andExpect(jsonPath("$.capturadoEm").value("2026-01-15T00:11:55Z"));
  }

  @Test
  void retornaTimeoutComo504() throws Exception {
    given(service.obterResultadoOficial(anyString()))
        .willThrow(new UpstreamTimeoutException("timeout", new RuntimeException("boom")));

    mockMvc.perform(get("/v1/maismilionaria/resultado-oficial"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.error").value("UPSTREAM_TIMEOUT"))
        .andExpect(jsonPath("$.details").isArray());
  }

  @Test
  void retornaBadResponseComo502() throws Exception {
    given(service.obterResultadoOficial(anyString()))
        .willThrow(new UpstreamBadResponseException("bad", List.of("Elemento de resultado nao encontrado")));

    mockMvc.perform(get("/v1/maismilionaria/resultado-oficial"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("UPSTREAM_BAD_RESPONSE"))
        .andExpect(jsonPath("$.details[0]").value("Elemento de resultado nao encontrado"));
  }
}
