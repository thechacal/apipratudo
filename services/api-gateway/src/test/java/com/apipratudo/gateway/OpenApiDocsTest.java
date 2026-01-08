package com.apipratudo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "app.firestore.enabled=false")
@AutoConfigureMockMvc
@Import(TestSupportConfig.class)
class OpenApiDocsTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void openApiYamlServed() throws Exception {
    MvcResult result = mockMvc.perform(get("/openapi.yaml"))
        .andExpect(status().isOk())
        .andReturn();

    String body = result.getResponse().getContentAsString();
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
}
