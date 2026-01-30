# Kit de divulgacao — Agendamento (MVP)

## Texto curto (WhatsApp / grupos)
API Pra Tudo agora tem Agendamento por API.
Crie servicos, liste slots, reserve e confirme.
Evite no-show com multas e eventos por webhook.
Tudo no mesmo /v1 com X-Api-Key.
Quer testar? Integre em minutos e veja os exemplos.

## Texto medio (LinkedIn)
A API Pra Tudo ganhou um MVP de Agendamento para negocios que dependem de horario.
Com endpoints simples voce cadastra servicos, lista slots, reserva e confirma.
Ha idempotencia para operacoes criticas, eventos para notificacao e controle de no-show.
Tudo roda no gateway /v1, com seguranca por X-Api-Key e quota padrao.
Ideal para clinicas, saloes e oficinas que querem integrar rapido.

## Como integrar em 60s (3 curls)
```bash
# criar servico
curl -s "$BASE_URL/v1/servicos" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: srv-001" \
  -H "Content-Type: application/json" \
  -d '{"name":"Corte Masculino","durationMin":40,"prepMin":5,"bufferMin":10,"noShowFeeCents":2000,"active":true}'

# slots disponiveis
curl -s "$BASE_URL/v1/slots-disponiveis?serviceId=srv_...&date=2026-01-26&agendaId=main" \
  -H "X-Api-Key: SUA_API_KEY"

# reservar e confirmar
curl -s "$BASE_URL/v1/reservar" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: res-001" \
  -H "Content-Type: application/json" \
  -d '{"serviceId":"srv_...","agendaId":"main","startAt":"2026-01-26T12:00:00Z","customer":{"name":"Ana","phone":"+5588999990000","email":"ana@email.com"}}'

curl -s "$BASE_URL/v1/confirmar" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: conf-001" \
  -H "Content-Type: application/json" \
  -d '{"appointmentId":"apt_..."}'
```

## Endpoints (lista)
- /v1/servicos
- /v1/agenda
- /v1/slots-disponiveis
- /v1/reservar
- /v1/confirmar
- /v1/cancelar
- /v1/notificar
- /v1/agendas
- /v1/agendas/{id}
- /v1/agendas/{id}/creditos/upgrade
- /v1/agendas/{id}/creditos/status/{chargeId}
- /v1/atendido
- /v1/multas
- /v1/multas/{id}/waive

## Links
- Swagger: /swagger-ui/index.html
- OpenAPI: /openapi.yaml
- Site: apipratudo.com
