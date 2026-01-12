package com.apipratudo.quota.docs;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenApiRedirectController {

  @GetMapping("/openapi.yaml")
  public ResponseEntity<Void> redirectToOpenApi() {
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create("/v3/api-docs.yaml"));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }
}
