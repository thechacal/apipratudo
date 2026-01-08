package com.apipratudo.gateway.docs;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootRedirectController {

  @GetMapping("/")
  public String redirectToDocs() {
    return "redirect:/docs";
  }
}
