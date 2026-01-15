# lotofacil-results-service

Servico para scraping ao vivo do resultado oficial da Lotofacil (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8084/v1/lotofacil/resultado-oficial | jq
```
