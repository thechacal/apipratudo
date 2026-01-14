# federal-results-service

Servico para scraping ao vivo do resultado oficial da Loteria Federal (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8083/v1/federal/resultado-oficial | jq
```
