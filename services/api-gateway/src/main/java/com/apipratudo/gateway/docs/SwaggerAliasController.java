package com.apipratudo.gateway.docs;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwaggerAliasController {

  @GetMapping("/swagger-ui")
  public ResponseEntity<Void> swaggerUiAlias() {
    return redirect("/swagger-ui/index.html");
  }

  @GetMapping("/swagger")
  public ResponseEntity<Void> swaggerAlias() {
    return redirect("/swagger-ui/index.html");
  }

  @GetMapping("/openapi.yaml")
  public ResponseEntity<Void> openApiAlias() {
    return redirect("/v3/api-docs.yaml");
  }

  private ResponseEntity<Void> redirect(String location) {
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create(location));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }
}
