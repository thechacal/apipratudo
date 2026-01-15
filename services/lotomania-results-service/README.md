# lotomania-results-service

Servico para scraping ao vivo do resultado oficial da Lotomania (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8087/v1/lotomania/resultado-oficial | jq
```
