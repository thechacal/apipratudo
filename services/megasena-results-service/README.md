# megasena-results-service

Servico para scraping ao vivo do resultado oficial da Mega-Sena (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8085/v1/megasena/resultado-oficial | jq
```
