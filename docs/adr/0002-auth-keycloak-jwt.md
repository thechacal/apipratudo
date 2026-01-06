# ADR 0002: Auth com Keycloak/JWT

Status: accepted

## Context
Precisamos de autenticacao padronizada e tokens para proteger APIs externas.

## Decision
Usar identity/keycloak como provedor OIDC, emitindo JWT validados pelo api-gateway e servicos.

## Consequences
- Integra OIDC e facilita SSO.
- Exige gestao de realms, clients e chaves de assinatura.
