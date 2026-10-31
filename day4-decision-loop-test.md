# Day 4 Test: Decision Loop, Idempotency, Manual Ack, Retry and DLT

Verified in GitHub Codespaces. Goal: payment-service reads the fraud verdict and changes `PENDING` to `APPROVED`, `FLAGGED` or `BLOCKED`. fraud-service skips duplicate events, commits offsets manually, and sends a failing event to `payment-events.DLT` after 3 retries.

## What Day 4 adds

| Feature | Where | Behavior |
|---|---|---|
| Decision consumer | payment-service `FraudDecisionConsumer` | Reads `fraud-decisions` and updates the payment status |
| Idempotent status update | payment-service `PaymentService.applyDecision` | Only a `PENDING` payment is changed; later decisions are ignored |
| Idempotent fraud check | fraud-service `FraudProcessingService` | A payment that already has a stored verdict is skipped |
| Manual ack | fraud-service `PaymentEventConsumer` | Offset is committed only after processing succeeds |
| Retry and DLT | fraud-service `KafkaErrorHandlingConfig` | 1 attempt plus 3 retries, 1 second apart, then `payment-events.DLT` |
| Failure simulation | `fraud.simulate-failure: true` | A payment with merchant `SIMULATE_FAILURE` always fails (demo only) |

## 1. Run the unit tests

```bash
cd payment-service
./mvnw clean test -Dtest=PaymentServiceDecisionTest
cd ../fraud-service
./mvnw clean test -Dtest='*RuleTest,FraudEngineTest'
```

Expected: payment-service `Tests run: 2, Failures: 0`, fraud-service `Tests run: 12, Failures: 0`.

| Test | Expected |
|---|---|
| `pendingPaymentBecomesBlocked` | A `PENDING` payment becomes `BLOCKED` and keeps the reason |
| `finalStatusIsNotOverwritten` | An `APPROVED` payment stays `APPROVED` after a later `BLOCKED` decision |

## 2. Start infrastructure

From the project root:

```bash
cd ..
docker compose up -d
sleep 25
docker compose ps
```

Expected: `kafka`, `kafka-ui`, `mysql` show `Up` for more than 20 seconds.

## 3. Check the configuration

In `fraud-service/src/main/resources/application.yml`:

- `fraud:` is a top-level key (same level as `spring:`), with `simulate-failure: true` under it.
- `enable-auto-commit: false` is under `spring.kafka.consumer`.
- `ack-mode: manual_immediate` is under `spring.kafka.listener`.

In `payment-service/src/main/resources/application.yml`: a `consumer:` block with `group-id: payment-service-group` and the `FraudDecisionEvent` default type.

## 4. Start the services

```bash
# terminal 1
cd fraud-service
chmod +x mvnw
./mvnw spring-boot:run
```

```bash
# terminal 2
cd payment-service
chmod +x mvnw
./mvnw spring-boot:run
```

Expected: both show `Started ...Application`.

## 5. How to read the results

Send each `curl` from **terminal 3**. The `curl` response always shows `"status":"PENDING"`, because the verdict arrives a moment later. Check the final status with this command (run it 2 seconds after each scenario):

```bash
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e \
"SELECT card_id, status, status_reason FROM paymentdb.payments ORDER BY created_at DESC LIMIT 10;"
```

Use a **different cardId for each scenario**, because fraud history is kept per card.

## Scenario D1: Normal payment becomes APPROVED

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-D1","amount":2500,"merchant":"Amazon","city":"Pune"}'
```

Expected:
- `curl` response: `201`, status `PENDING`
- fraud-service log: `Decision APPROVED for payment <id> published`
- payment-service log: `Received decision APPROVED`, then `Payment <id> is now APPROVED`
- Database: status `APPROVED`

## Scenario D2: 4th rapid payment becomes BLOCKED (velocity)

Paste all four commands together:

```bash
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-D2","amount":100,"merchant":"Amazon","city":"Pune"}'
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-D2","amount":100,"merchant":"Amazon","city":"Pune"}'
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-D2","amount":100,"merchant":"Amazon","city":"Pune"}'
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-D2","amount":100,"merchant":"Amazon","city":"Pune"}'
```

Expected in the database: three `APPROVED` and one `BLOCKED`, with `status_reason` containing `VELOCITY`.

## Scenario D3: High amount becomes FLAGGED

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-D3","amount":150000,"merchant":"Croma","city":"Pune"}'
```

Expected: status `FLAGGED`, `status_reason` contains `AMOUNT`.

## Scenario D4: Different city within 10 minutes becomes BLOCKED (geo)

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-D4","amount":100,"merchant":"Amazon","city":"Pune"}'

curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-D4","amount":100,"merchant":"Amazon","city":"Mumbai"}'
```

Expected: first `APPROVED`, second `BLOCKED` with `GEO_MISMATCH` in the reason.

## Scenario D5: Duplicate payment event is skipped (fraud-service idempotency)

First get the paymentId of the `CARD-D1` payment:

```bash
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e \
"SELECT payment_id FROM frauddb.transactions WHERE card_id='CARD-D1';"
```

Replay its event (replace `PASTE_ID` with the id):

```bash
echo 'CARD-D1:{"paymentId":"PASTE_ID","cardId":"CARD-D1","amount":2500,"merchant":"Amazon","city":"Pune","createdAt":"2026-10-30T10:00:00Z"}' | \
docker exec -i $(docker compose ps -q kafka) /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:29092 --topic payment-events \
  --property parse.key=true --property key.separator=:
```

Expected:
- fraud-service log: `Duplicate payment <id> skipped`
- No new row in `frauddb.transactions` for that payment (the count stays at 1):
  ```bash
  docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e \
  "SELECT COUNT(*) FROM frauddb.transactions WHERE payment_id='PASTE_ID';"
  ```
- No new decision published

## Scenario D6: Duplicate decision is ignored (payment-service idempotency)

Get the paymentId of the `APPROVED` `CARD-D1` payment:

```bash
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e \
"SELECT id, status FROM paymentdb.payments WHERE card_id='CARD-D1';"
```

Send a conflicting decision for it (replace `PASTE_ID`):

```bash
echo 'CARD-D1:{"paymentId":"PASTE_ID","cardId":"CARD-D1","decision":"BLOCKED","reason":"manual test","decidedAt":"2026-10-30T10:00:00Z"}' | \
docker exec -i $(docker compose ps -q kafka) /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:29092 --topic fraud-decisions \
  --property parse.key=true --property key.separator=:
```

Expected:
- payment-service log: `Payment <id> already APPROVED, ignoring duplicate decision`
- Database: status is still `APPROVED`

## Scenario D7: Failing event is retried 3 times, then goes to the DLT

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-D7","amount":100,"merchant":"SIMULATE_FAILURE","city":"Pune"}'
```

Expected in the fraud-service log: the `Simulated failure for payment <id>` error appears 4 times, about 1 second apart (1 attempt plus 3 retries). The payment stays `PENDING` in the database.

Read the dead-letter topic:

```bash
docker exec -it $(docker compose ps -q kafka) /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:29092 --topic payment-events.DLT --from-beginning \
  --property print.key=true --property print.partition=true --timeout-ms 5000
```

Expected: one line with key `CARD-D7` and the original payment JSON.

## Scenario D8: Manual ack, the failed event does not come back

1. Press `Ctrl+C` in the fraud-service terminal.
2. Start it again: `./mvnw spring-boot:run`.

Expected: no new `Simulated failure` lines for the `CARD-D7` payment, so the retry cycle does not repeat. The offset was committed after the record reached the DLT.

## Scenario D9: Fault tolerance with the full loop

1. Stop fraud-service with `Ctrl+C`.
2. Send a payment:
   ```bash
   curl -X POST http://localhost:8081/api/v1/payments \
     -H "Content-Type: application/json" \
     -d '{"cardId":"CARD-D9","amount":500,"merchant":"Swiggy","city":"Delhi"}'
   ```
   Expected: `201`, status `PENDING`.
3. Wait 10 seconds. The database still shows `PENDING`.
4. Start fraud-service again.

Expected: within a few seconds the payment becomes `APPROVED`. Kafka kept the event while fraud-service was down.

## Scenario D10: Invalid input is rejected before the loop

```bash
curl -i -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-D10","amount":-5,"merchant":"Amazon","city":"Pune"}'
```

Expected: HTTP `400`, no row in `paymentdb.payments`, and nothing in either service log.

## Scenario D11: Old payments from Day 3 also resolve

The new `payment-service-group` reads `fraud-decisions` from the beginning. Check the older rows:

```bash
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e \
"SELECT status, COUNT(*) FROM paymentdb.payments GROUP BY status;"
```

Expected: payments from Day 3 that were `PENDING` now show `APPROVED`, `FLAGGED` or `BLOCKED`. Only the `CARD-D7` payment (the simulated failure) stays `PENDING`.

## 6. Verify the topics from the terminal

List all topics:

```bash
docker exec -it $(docker compose ps -q kafka) /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:29092 --list
```

Expected: `payment-events`, `payment-events.DLT`, `fraud-decisions`.

Read the verdicts and check the partitions:

```bash
docker exec -it $(docker compose ps -q kafka) /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:29092 --topic fraud-decisions --from-beginning \
  --property print.key=true --property print.partition=true --timeout-ms 5000
```

Expected: every `CARD-D2` line shows the same `Partition:N`.

## 7. Reset between runs (optional)

```bash
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e \
"TRUNCATE frauddb.transactions; TRUNCATE paymentdb.payments;"
```

## 8. Clean up

```bash
# Ctrl+C in both app terminals, then:
docker compose stop     # keeps data
docker compose down     # removes containers
docker compose down -v  # removes containers and database data
```

Set `fraud.simulate-failure` to `false` before demos or publishing. Stop the Codespace from the bottom-left menu to save free hours.

## Day 4 checklist

- [ ] payment-service decision tests pass (2)
- [ ] fraud-service rule tests still pass (12)
- [ ] D1: payment becomes `APPROVED`
- [ ] D2: 4th rapid payment becomes `BLOCKED`
- [ ] D3: high amount becomes `FLAGGED`
- [ ] D4: Pune then Mumbai becomes `BLOCKED`
- [ ] D5: duplicate payment event skipped
- [ ] D6: duplicate decision ignored, status unchanged
- [ ] D7: 4 failed attempts, then a message in `payment-events.DLT`
- [ ] D8: failed event does not return after restart
- [ ] D9: payment resolves after fraud-service restarts
- [ ] D10: invalid input returns `400`
- [ ] D11: older `PENDING` payments resolve
- [ ] Same card, same partition in `fraud-decisions`

## Fixes applied during Day 4

| Problem | Fix |
|---|---|
| (fill in what you debugged) | (fill in the fix) |

## Known limitations

- The payment is saved and the event is published in two separate steps. If Kafka is down at that moment, the payment stays `PENDING` with no event. The standard fix is the transactional outbox pattern (future improvement).
- The DLT is written but not read by anything. A real system would alert on it and offer a replay tool.
- `fraud.simulate-failure` is a demo hook and should be off in any real deployment.

## Common problems

| Symptom | Fix |
|---|---|
| Status stays `PENDING` forever | Check the payment-service log for `Received decision`. If missing, check the `consumer:` block, the group id and the deserializer class name. |
| `Class is not in the trusted packages` | Set `trusted.packages` to `com.rohan.payment.dto` in payment-service. |
| `No qualifying bean KafkaTemplate<Object, Object>` | Make sure the `producer:` block exists in fraud-service `application.yml`. |
| App fails to start after editing YAML | Check for two `spring:` blocks, tabs, or `fraud:` placed under `spring:`. |
| D7 shows only one failure and no retries | `KafkaErrorHandlingConfig` is not being picked up. Check `@Configuration` and the package. |
| D7 shows retries forever | The `payment-events.DLT` topic is missing or has fewer partitions than `payment-events`. |
| `Simulated failure` never happens | `fraud.simulate-failure` is not `true`, or the merchant is not exactly `SIMULATE_FAILURE`. |
| D5 or D6 console producer prints nothing and nothing arrives | Use `docker exec -i` (not `-it`) when piping, and check the `key:value` separator. |
| `500` on `POST` | Kafka is unhealthy. Run `docker compose ps` and `docker compose logs kafka --tail 30`. |
| Kafka UI offline | Use the terminal commands in section 6 instead. |
