# scheduling-service-v0.1.0 — Agendamento (MVP)

## O que foi entregue
- Microservico de agendamento interno com token
- Rotas publicas de agendamento no gateway /v1
- Idempotencia para operacoes mutaveis
- Creditos de agenda para confirmar atendimentos
- Multas e eventos via webhook (best-effort)
- Script de smoke local

## Como usar (headers e endpoints)
Headers:
- `X-Api-Key` (cliente)
- `Idempotency-Key` (POST/PATCH mutaveis)

Principais endpoints publicos:
- `GET /v1/servicos`
- `POST /v1/servicos`
- `GET /v1/agenda`
- `GET /v1/slots-disponiveis`
- `POST /v1/reservar`
- `POST /v1/confirmar`
- `POST /v1/cancelar`
- `POST /v1/notificar`
- `GET /v1/agendas`
- `POST /v1/agendas`
- `GET /v1/agendas/{id}`
- `PATCH /v1/agendas/{id}`
- `POST /v1/agendas/{id}/creditos/upgrade`
- `GET /v1/agendas/{id}/creditos/status/{chargeId}`
- `POST /v1/atendido`
- `GET /v1/multas`
- `POST /v1/multas/{id}/waive`

## Rodar local (minimo)
```bash
export APP_FIRESTORE_ENABLED=false
export SCHEDULING_SERVICE_TOKEN=changeme
export SCHEDULING_AGENDA_DEFAULT_CREDITS=1

mvn -B -ntp -f services/scheduling-service/pom.xml spring-boot:run
mvn -B -ntp -f services/api-gateway/pom.xml spring-boot:run
```

## Smoke
```bash
GW_URL=http://localhost:8080 ./scripts/smoke-scheduling-local.sh
```
