package com.apipratudo.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void openApiYamlIncludesSecuritySchemes() throws Exception {
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

    Map<String, Object> spec = new ObjectMapper(new YAMLFactory())
        .readValue(body, new TypeReference<>() {});

    Map<String, Object> components = map(spec.get("components"));
    Map<String, Object> securitySchemes = map(components.get("securitySchemes"));
    assertThat(securitySchemes).containsKeys("AdminToken", "InternalToken", "PortalToken", "ApiKeyAuth");

    Map<String, Object> paths = map(spec.get("paths"));
    Map<String, Object> apiKeys = map(paths.get("/v1/api-keys"));
    assertSecurityRequirement(apiKeys.get("post"), "AdminToken");

    Map<String, Object> consume = map(paths.get("/v1/quota/consume"));
    assertSecurityRequirement(consume.get("post"), "InternalToken");

    Map<String, Object> statusOp = map(paths.get("/v1/quota/status"));
    List<Map<String, Object>> security = list(map(statusOp.get("get")).get("security"));
    assertThat(security).anyMatch(item -> item.containsKey("AdminToken"));
    assertThat(security).anyMatch(item -> item.containsKey("InternalToken"));
    assertThat(security).anyMatch(item -> item.containsKey("ApiKeyAuth"));

    Map<String, Object> createFree = map(paths.get("/v1/internal/keys/create-free"));
    assertSecurityRequirement(createFree.get("post"), "PortalToken");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> list(Object value) {
    return (List<Map<String, Object>>) value;
  }

  private void assertSecurityRequirement(Object operation, String scheme) {
    Map<String, Object> op = map(operation);
    List<Map<String, Object>> security = list(op.get("security"));
    assertThat(security).anyMatch(item -> item.containsKey(scheme));
  }
}
