package com.apipratudo.gateway.docs;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootRedirectController {

  @GetMapping("/")
  public ResponseEntity<Void> redirectToDocs() {
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create("/docs"));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }
}
