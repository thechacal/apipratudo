# ADR 0001: API Gateway

Status: accepted

## Context
O apipratudo tera multiplos microservicos. Precisamos de um ponto unico para roteamento, autenticacao e politicas comuns.

## Decision
Adotar um api-gateway (Spring Boot) como front door para todas as APIs /v1.

## Consequences
- Centraliza politicas e simplifica o consumo.
- Requer alta disponibilidade e monitoramento dedicado.
