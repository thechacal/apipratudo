# loteca-results-service

Servico para scraping ao vivo do resultado oficial da Loteca (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8090/v1/loteca/resultado-oficial | jq
```
