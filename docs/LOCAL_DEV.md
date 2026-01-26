# Local Dev (resumo)

## Portas padrao

| Servico | Porta |
| --- | --- |
| api-gateway | 8080 |
| quota-service | 8081 |
| developer-portal-service | 8094 |
| billing-saas-service | 8096 |

## Variaveis (dev)

| Variavel | Servico(s) | Uso |
| --- | --- | --- |
| APP_FIRESTORE_ENABLED | todos | Ativa/desativa Firestore no local. |
| PORTAL_TOKEN | quota-service, developer-portal-service | Token para criacao de chaves FREE. |
| INTERNAL_TOKEN | quota-service | Token interno para consume/refund. |
| QUOTA_INTERNAL_TOKEN | api-gateway | Token usado pelo gateway ao chamar quota. |
| BILLING_SAAS_SERVICE_TOKEN | api-gateway, billing-saas-service | Token interno do billing-saas. |
| BILLING_SAAS_WEBHOOK_SECRET | api-gateway, billing-saas-service | Segredo do webhook PIX. |
| BILLING_SAAS_MASTER_KEY_BASE64 | billing-saas-service | Master key AES-GCM para tokens PagBank. |

## Subir local (sem reactor root)

```bash
mvn -B -ntp -f services/quota-service/pom.xml spring-boot:run
mvn -B -ntp -f services/developer-portal-service/pom.xml spring-boot:run
mvn -B -ntp -f services/billing-saas-service/pom.xml spring-boot:run
mvn -B -ntp -f services/api-gateway/pom.xml spring-boot:run
```
