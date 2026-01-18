up:
	docker compose -f docker-compose.local.yml up --build

down:
	docker compose -f docker-compose.local.yml down

logs:
	docker compose -f docker-compose.local.yml logs -f

test:
	mvn -B -ntp test

run-local:
	@echo "Use SPRING_PROFILES_ACTIVE=local em cada servico:"
	@echo "  cd services/quota-service && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
	@echo "  cd services/webhook-service && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
	@echo "  cd services/api-gateway && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"

smoke-cloud:
	./scripts/smoke-cloud.sh

smoke-cloud-summary:
	SMOKE_SUMMARY=1 ./scripts/smoke-cloud.sh

smoke-cloud-json:
	SHOW_JSON=1 ./scripts/smoke-cloud.sh

help:
	@echo "Targets disponiveis:"
	@echo "  up                   - docker compose up --build"
	@echo "  down                 - docker compose down"
	@echo "  logs                 - docker compose logs -f"
	@echo "  test                 - mvn -B -ntp test"
	@echo "  run-local            - instrucoes para rodar servicos localmente"
	@echo "  smoke-cloud          - smoke test via Cloud Run"
	@echo "  smoke-cloud-summary  - smoke test com resumo"
	@echo "  smoke-cloud-json     - smoke test com JSON completo"
