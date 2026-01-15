# maismilionaria-results-service

Servico para scraping ao vivo do resultado oficial da Mais Milionaria (CAIXA).

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoint
```bash
curl -s http://localhost:8093/v1/maismilionaria/resultado-oficial | jq
```
