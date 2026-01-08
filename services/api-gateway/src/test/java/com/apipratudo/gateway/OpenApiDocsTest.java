package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void openApiYamlServed() throws Exception {
    MvcResult result = mockMvc.perform(get("/openapi.yaml"))
        .andReturn();

    int status = result.getResponse().getStatus();
    assertThat(status).isIn(302, 307);
    String location = result.getResponse().getHeader("Location");
    assertThat(location).isEqualTo("/v3/api-docs.yaml");

    MvcResult follow = mockMvc.perform(get(location))
        .andExpect(status().isOk())
        .andReturn();

    String body = follow.getResponse().getContentAsString();
    assertThat(body).contains("openapi: 3.");
  }

  @Test
  void swaggerUiAvailable() throws Exception {
    MvcResult result = mockMvc.perform(get("/docs"))
        .andReturn();

    int status = result.getResponse().getStatus();
    if (status >= 300 && status < 400) {
      String location = result.getResponse().getHeader("Location");
      assertThat(location).isNotBlank();
      assertThat(location).startsWith("/");

      if (location.startsWith("http")) {
        URI uri = URI.create(location);
        String path = uri.getRawPath();
        if (uri.getRawQuery() != null) {
          path = path + "?" + uri.getRawQuery();
        }
        location = path;
      }

      MvcResult follow = mockMvc.perform(get(location))
          .andExpect(status().isOk())
          .andReturn();

      assertThat(follow.getResponse().getContentAsString()).contains("Swagger UI");
      return;
    }

    assertThat(status).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString()).contains("Swagger UI");
  }

  @Test
  void rootRedirectsToDocsWithRelativeLocation() throws Exception {
    MvcResult result = mockMvc.perform(get("/"))
        .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(302);
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/docs");
  }

  @Test
  void swaggerAliasesRedirect() throws Exception {
    MvcResult swaggerUi = mockMvc.perform(get("/swagger-ui"))
        .andReturn();
    assertThat(swaggerUi.getResponse().getStatus()).isEqualTo(302);
    assertThat(swaggerUi.getResponse().getHeader("Location")).isEqualTo("/swagger-ui/index.html");

    MvcResult swagger = mockMvc.perform(get("/swagger"))
        .andReturn();
    assertThat(swagger.getResponse().getStatus()).isEqualTo(302);
    assertThat(swagger.getResponse().getHeader("Location")).isEqualTo("/swagger-ui/index.html");
  }
}
