package com.apipratudo.gateway.docs;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocsRedirectController {

  @GetMapping("/docs")
  public String redirectToIndex() {
    return "redirect:/swagger-ui/index.html";
  }
}
