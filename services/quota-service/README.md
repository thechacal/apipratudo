# quota-service

Servico de quotas e rate limits para o apipratudo.

## Como rodar localmente

```bash
mvn spring-boot:run
```

Por padrao o servico sobe em `http://localhost:8081` e o Swagger UI em `/docs`.

## Firestore

O servico usa Firestore quando `APP_FIRESTORE_ENABLED=true` (default).

- `APP_FIRESTORE_PROJECT_ID` (ou `GOOGLE_CLOUD_PROJECT`)
- `APP_FIRESTORE_EMULATOR_HOST` (opcional, ex: `localhost:8085`)

Para rodar com emulator:

```bash
export APP_FIRESTORE_EMULATOR_HOST=localhost:8085
export APP_FIRESTORE_PROJECT_ID=local-dev
mvn spring-boot:run
```

Para rodar em memoria (sem Firestore):

```bash
export APP_FIRESTORE_ENABLED=false
mvn spring-boot:run
```
