# duplasena-results-service

Servico para scraping ao vivo do resultado oficial da Dupla Sena (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8089/v1/duplasena/resultado-oficial | jq
```
