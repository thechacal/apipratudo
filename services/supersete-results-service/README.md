# supersete-results-service

Servico para scraping ao vivo do resultado oficial da Super Sete (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8092/v1/supersete/resultado-oficial | jq
```
