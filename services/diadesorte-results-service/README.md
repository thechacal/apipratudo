# diadesorte-results-service

Servico para scraping ao vivo do resultado oficial da Dia de Sorte (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8091/v1/diadesorte/resultado-oficial | jq
```
