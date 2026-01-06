# Architecture

## Visao geral (C4)
O apipratudo e uma plataforma de APIs com entrada unica via api-gateway. Os consumidores acessam microservicos especializados, com identidade centralizada (identity/keycloak) e observabilidade unificada. Os diagramas C4 estao em `docs/diagrams/`.

## Objetivos
- Padronizar APIs REST versionadas.
- Isolar responsabilidades por microservico.
- Centralizar autenticacao e autorizacao.
- Permitir escalabilidade independente por servico.
- Garantir observabilidade ponta a ponta.

## Decisoes principais
- api-gateway como ponto unico de entrada para /v1.
- Servicos Spring Boot separados por dominio.
- identity/keycloak para OIDC e JWT.
- Persistencia principal em PostgreSQL e cache/fila em Redis.
- OpenTelemetry para traces, logs e metrics.
