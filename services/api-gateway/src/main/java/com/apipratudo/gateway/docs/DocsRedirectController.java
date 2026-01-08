package com.apipratudo.gateway.docs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(name = "app.docs.redirect-enabled", havingValue = "true")
public class DocsRedirectController {

  @GetMapping("/docs")
  public String redirectToIndex() {
    return "redirect:/swagger-ui/index.html";
  }
}
