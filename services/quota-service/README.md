# quota-service

Servico de quotas e rate limits para o apipratudo.

## Como rodar localmente

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Ou via env:
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Por padrao o servico sobe em `http://localhost:8081` e o Swagger UI em `/docs`.

## Tokens locais

No profile `local`:
- `X-Admin-Token: dev-admin`
- `X-Internal-Token: dev-internal`

## Firestore

O servico usa Firestore quando `APP_FIRESTORE_ENABLED=true` (default).

- `APP_FIRESTORE_PROJECT_ID` (ou `GOOGLE_CLOUD_PROJECT`)
- `APP_FIRESTORE_EMULATOR_HOST` (opcional, ex: `localhost:8085`)

Para rodar com emulator:

```bash
export APP_FIRESTORE_EMULATOR_HOST=localhost:8085
export APP_FIRESTORE_PROJECT_ID=local-dev
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Para rodar em memoria (sem Firestore):

```bash
export APP_FIRESTORE_ENABLED=false
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Exemplos curl

Criar API key (admin):
```bash
curl -X POST http://localhost:8081/v1/api-keys \
  -H 'Content-Type: application/json' \
  -H 'X-Admin-Token: dev-admin' \
  -d '{"name":"Cliente A","owner":"cliente-a","limits":{"requestsPerMinute":60,"requestsPerDay":1000}}'
```

Consumir quota (internal):
```bash
curl -X POST http://localhost:8081/v1/quota/consume \
  -H 'Content-Type: application/json' \
  -H 'X-Internal-Token: dev-internal' \
  -d '{"apiKey":"<API_KEY>","requestId":"req-123","route":"GET /v1/webhooks","cost":1}'
```
