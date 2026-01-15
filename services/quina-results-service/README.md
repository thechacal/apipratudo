# quina-results-service

Servico para scraping ao vivo do resultado oficial da Quina (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8086/v1/quina/resultado-oficial | jq
```
