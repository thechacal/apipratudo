package com.apipratudo.portal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyFilter apiKeyFilter) throws Exception {
    http.csrf(csrf -> csrf.disable());
    http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/**", "/docs", "/swagger-ui/**", "/v3/api-docs/**", "/openapi.yaml")
        .permitAll()
        .anyRequest()
        .permitAll());
    http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);
    http.formLogin(form -> form.disable());
    http.httpBasic(basic -> basic.disable());
    return http.build();
  }
}
