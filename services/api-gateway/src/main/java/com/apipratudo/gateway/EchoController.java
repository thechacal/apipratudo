package com.apipratudo.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EchoController {

  @GetMapping("/v1/echo")
  public EchoResponse echo() {
    return new EchoResponse("api-gateway", "ok");
  }

  public record EchoResponse(String service, String status) {
  }
}
