up:
\tdocker compose -f docker-compose.local.yml up --build

down:
\tdocker compose -f docker-compose.local.yml down

logs:
\tdocker compose -f docker-compose.local.yml logs -f

test:
\tmvn -B -ntp test

run-local:
\t@echo "Use SPRING_PROFILES_ACTIVE=local em cada servico:"
\t@echo "  cd services/quota-service && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
\t@echo "  cd services/webhook-service && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
\t@echo "  cd services/api-gateway && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"

smoke-cloud:
\t./scripts/smoke-cloud.sh

smoke-cloud-summary:
\tSMOKE_SUMMARY=1 ./scripts/smoke-cloud.sh

smoke-cloud-json:
\tSMOKE_SHOW_JSON=1 ./scripts/smoke-cloud.sh
