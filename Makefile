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

help:
\t@echo "Targets disponiveis:"
\t@echo "  up                   - docker compose up --build"
\t@echo "  down                 - docker compose down"
\t@echo "  logs                 - docker compose logs -f"
\t@echo "  test                 - mvn -B -ntp test"
\t@echo "  run-local            - instrucoes para rodar servicos localmente"
\t@echo "  smoke-cloud          - smoke test via Cloud Run"
\t@echo "  smoke-cloud-summary  - smoke test com resumo"
\t@echo "  smoke-cloud-json     - smoke test com JSON completo"
