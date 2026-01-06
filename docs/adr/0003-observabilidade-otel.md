# ADR 0003: Observabilidade com OpenTelemetry

Status: accepted

## Context
Precisamos de traces, logs e metrics consistentes entre microservicos.

## Decision
Padronizar instrumentacao via OpenTelemetry SDK e coletar sinais no otel-collector.

## Consequences
- Telemetria padronizada e correlacionada.
- Requer instrumentacao em todos os servicos.
