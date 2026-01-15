# timemania-results-service

Servico para scraping ao vivo do resultado oficial da Timemania (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8088/v1/timemania/resultado-oficial | jq
```
