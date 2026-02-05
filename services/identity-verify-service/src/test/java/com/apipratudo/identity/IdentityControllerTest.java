package com.apipratudo.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.security.service-token=test-token"
})
class IdentityControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void invalidTipoReturns400() throws Exception {
    mockMvc.perform(post("/internal/v1/documentos/validar")
            .header("X-Service-Token", "test-token")
            .header("X-Tenant-Id", "tenant-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tipo\":\"RG\",\"documento\":\"123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void missingServiceTokenReturns401() throws Exception {
    mockMvc.perform(post("/internal/v1/documentos/validar")
            .header("X-Tenant-Id", "tenant-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tipo\":\"CPF\",\"documento\":\"52998224725\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void cnpjStatusReturnsUnknownWhenStructurallyValid() throws Exception {
    mockMvc.perform(get("/internal/v1/cnpj/04252011000110/status")
            .header("X-Service-Token", "test-token")
            .header("X-Tenant-Id", "tenant-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valido_estrutural").value(true))
        .andExpect(jsonPath("$.status").value("UNKNOWN"));
  }
}
