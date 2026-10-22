# Day 3 Test: Fraud Rules, Engine and Decision Events

Verified in GitHub Codespaces. Goal: fraud-service evaluates each payment against 3 rules (Velocity, Amount, Geo mismatch), saves the result to `frauddb.transactions`, and publishes a verdict to the `fraud-decisions` topic.

## Rules under test

| Rule | Condition | Result |
|---|---|---|
| Velocity | 3 or more earlier payments from the same card in the last 60 seconds (the 4th is blocked) | BLOCKED |
| Amount | Amount greater than 100000 | FLAGGED |
| Geo mismatch | Earlier payment from a different city in the last 10 minutes | BLOCKED |
| None | No rule triggered | APPROVED |

If several rules trigger, the most severe result wins (BLOCKED over FLAGGED over APPROVED) and all reasons are listed.

## 1. Run the unit tests (no Kafka, no database)

From the project root:

```bash
cd fraud-service
./mvnw clean test -Dtest='*RuleTest,FraudEngineTest'
```

Expected: `Tests run: 12, Failures: 0, Errors: 0`.

Output Screenshots: 
<img width="1986" height="530" alt="image" src="https://github.com/user-attachments/assets/b181d07f-3518-4672-93e8-1417ba556f39" />

| Test class | Tests |
|---|---|
| VelocityRuleTest | 3 (under the limit, 4th payment blocked, old payments ignored) |
| AmountRuleTest | 2 (exactly 100000 approved, 100000.01 flagged) |
| GeoMismatchRuleTest | 4 (same city, city case, different city within 10 min, different city after 10 min) |
| FraudEngineTest | 3 (normal, high amount, amount plus geo) |

## 2. Start infrastructure

```bash
cd ..          # back to the project root
docker compose up -d
sleep 25
docker compose ps
```

Expected: 3 containers (`kafka`, `kafka-ui`, `mysql`) show `Up` for more than 20 seconds.

Kafka UI: http://localhost:8080 (open from the Ports tab in Codespaces)

## 3. Start fraud-service (terminal 1)

```bash
cd fraud-service
chmod +x mvnw
./mvnw spring-boot:run
```

Expected: `Started FraudServiceApplication` and partitions assigned to `fraud-service-group`.

## 4. Start payment-service (terminal 2)

```bash
cd payment-service
chmod +x mvnw
./mvnw spring-boot:run
```

Expected: `Started PaymentServiceApplication` on port 8081.

## 5. How to read the results

Send each `curl` from **terminal 3**. Each one returns `201` with `"status":"PENDING"`, because payment status is updated only on Day 4. The fraud verdict appears in the **fraud-service log**:

```
Decision <APPROVED|FLAGGED|BLOCKED> for payment <id> published (partition P, offset O)
```

Use a **different cardId for each scenario**, because fraud history is kept per card.

## Scenario 1: Normal payment is approved

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S1","amount":2500.50,"merchant":"Amazon","city":"Pune"}'
```

Expected: `Decision APPROVED`.

## Scenario 2: Velocity, 4th rapid payment is blocked

Run these four commands quickly, one after another (or paste them together):

```bash
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-S2","amount":100,"merchant":"Amazon","city":"Pune"}'
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-S2","amount":100,"merchant":"Amazon","city":"Pune"}'
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-S2","amount":100,"merchant":"Amazon","city":"Pune"}'
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-S2","amount":100,"merchant":"Amazon","city":"Pune"}'
```

Expected: payments 1 to 3 `Decision APPROVED`, payment 4 `Decision BLOCKED` with a reason containing `VELOCITY`.

Output Screenshots:
<img width="1690" height="180" alt="image" src="https://github.com/user-attachments/assets/4ecd983f-f947-4621-8a3e-f3b3a87e570c" />
<img width="1690" height="328" alt="image" src="https://github.com/user-attachments/assets/3cc1fbf4-139d-486b-801a-46cf71a953f5" />
<img width="1692" height="556" alt="image" src="https://github.com/user-attachments/assets/bbb5da0b-7f36-437a-b13e-73610e66e325" />

## Scenario 3: Velocity, slow payments are not blocked

Send the first three, wait 65 seconds, then send the fourth:

```bash
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-S3","amount":100,"merchant":"Amazon","city":"Pune"}'
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-S3","amount":100,"merchant":"Amazon","city":"Pune"}'
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-S3","amount":100,"merchant":"Amazon","city":"Pune"}'
sleep 65
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" -d '{"cardId":"CARD-S3","amount":100,"merchant":"Amazon","city":"Pune"}'
```

Expected: all four `Decision APPROVED`, because the first three are older than 60 seconds when the 4th arrives.

## Scenario 4: Amount, high amount is flagged

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S4","amount":150000,"merchant":"Croma","city":"Pune"}'
```

Expected: `Decision FLAGGED` with a reason containing `AMOUNT`.

Output Screenshots:
<img width="1732" height="194" alt="image" src="https://github.com/user-attachments/assets/9dc7a0de-fa46-4826-8397-c05d5eec2953" />
<img width="1736" height="104" alt="image" src="https://github.com/user-attachments/assets/1007cd5c-6b2b-44e9-9f4b-fbad6f33fbe3" />
<img width="1736" height="104" alt="image" src="https://github.com/user-attachments/assets/d620c51a-c81b-4e0c-97b2-18bcbb37ae1d" />

## Scenario 5: Amount boundary, exactly 100000 is approved

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S5","amount":100000,"merchant":"Croma","city":"Pune"}'
```

Expected: `Decision APPROVED`.

## Scenario 6: Geo mismatch, different city within 10 minutes is blocked

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S6","amount":100,"merchant":"Amazon","city":"Pune"}'

curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S6","amount":100,"merchant":"Amazon","city":"Mumbai"}'
```

Expected: first `APPROVED`, second `BLOCKED` with a reason containing `GEO_MISMATCH`.

Output Screenshots:
<img width="1740" height="364" alt="image" src="https://github.com/user-attachments/assets/c62d5d03-37d9-4c2f-8a97-13eb749c3574" />
<img width="1724" height="162" alt="image" src="https://github.com/user-attachments/assets/849ae39f-d4d6-4a87-8bfe-9cfc978e8d0f" />
<img width="1722" height="328" alt="image" src="https://github.com/user-attachments/assets/f58386d6-b4c6-46d9-8214-d5fbbdf64db5" />

## Scenario 7: Geo, same city is approved

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S7","amount":100,"merchant":"Amazon","city":"Pune"}'

curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S7","amount":100,"merchant":"Amazon","city":"Pune"}'
```

Expected: both `APPROVED`.

## Scenario 8: Geo, city name case does not matter

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S8","amount":100,"merchant":"Amazon","city":"Pune"}'

curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S8","amount":100,"merchant":"Amazon","city":"pune"}'
```

Expected: both `APPROVED`.

## Scenario 9: Several rules at once, most severe wins

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S9","amount":100,"merchant":"Amazon","city":"Pune"}'

curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S9","amount":150000,"merchant":"Croma","city":"Mumbai"}'
```

Expected: first `APPROVED`, second `BLOCKED` with a reason containing both `AMOUNT` and `GEO_MISMATCH`.

## Scenario 10: Cards are independent

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S10A","amount":100,"merchant":"Amazon","city":"Pune"}'

curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S10B","amount":100,"merchant":"Amazon","city":"Mumbai"}'
```

Expected: both `APPROVED`, because they are different cards, so there is no geo mismatch.

## Scenario 11: Invalid input is rejected before fraud checks

```bash
curl -i -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-S11","amount":-5,"merchant":"Amazon","city":"Pune"}'
```

Expected: HTTP `400`, and nothing appears in the fraud-service log.

## 6. Verify the database

```bash
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e \
"SELECT card_id, city, amount, decision, reason FROM frauddb.transactions ORDER BY created_at;"
```

Expected: one row per payment sent in scenarios 1 to 10, with the decisions listed above.

Payment status is not updated yet (planned for Day 4):

```bash
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e \
"SELECT card_id, status FROM paymentdb.payments;"
```

Expected: every payment is still `PENDING`.

## 7. Verify in Kafka UI

1. Open http://localhost:8080
2. Go to **Topics -> fraud-decisions -> Messages**
3. Confirm messages with `paymentId`, `cardId`, `decision`, `reason`, `decidedAt`, and key = `cardId`
4. Confirm all messages for `CARD-S2` are in the **same partition**

## 8. Reset between runs (optional)

```bash
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e "TRUNCATE frauddb.transactions;"
```

## 9. Clean up

```bash
# Ctrl+C in both app terminals, then:
docker compose stop     # keeps data
docker compose down     # removes containers
docker compose down -v  # removes containers and database data
```

Stop the Codespace from the bottom-left menu to save free hours.

## Day 3 checklist (Main three rules tested)

- [x] 12 unit tests pass
- [x] Scenario 1: normal payment approved
- [x] Scenario 2: 4th rapid payment blocked (velocity)
- [ ] Scenario 3: slow payments approved
- [x] Scenario 4: 150000 flagged
- [ ] Scenario 5: 100000 approved
- [x] Scenario 6: Pune then Mumbai blocked
- [ ] Scenario 7: same city approved
- [ ] Scenario 8: city case ignored
- [ ] Scenario 9: amount plus geo gives BLOCKED with both reasons
- [ ] Scenario 10: different cards are independent
- [ ] Scenario 11: invalid input returns 400
- [ ] Rows visible in `frauddb.transactions`
- [ ] `fraud-decisions` messages visible in Kafka UI

## Fixes applied during Day 3

| Problem | Fix |
|---|---|
| `GeoMismatchRuleTest` `approvesSameCity` and `ignoresCityCase` failed (expected APPROVED, was BLOCKED) | `TransactionRecord.getCity()` returned `paymentId` instead of `city`. Changed it to `return city;`. |
| `reason` column declared `nullable = false` | Changed to `@Column(length = 500)` so a null reason cannot break a save. |
| `grep` said `No such file or directory` | The terminal was already inside `fraud-service`. Use `pwd` to check, and use paths relative to it. |
| Test count showed 12, not 10 | 12 is correct: 3 + 2 + 4 + 3. |

## Known limitations (planned for Day 4)

- Payment status is not updated yet, because payment-service does not read `fraud-decisions`.
- A duplicate event is processed again (idempotency comes on Day 4).
- A failing event is not retried or sent to a dead-letter topic yet.

## Common problems

| Symptom | Fix |
|---|---|
| Same-city payments blocked | Check that `TransactionRecord.getCity()` returns `city`. |
| Test passes locally but fails after a change | Use `./mvnw clean test` so old compiled classes are not reused. |
| `No qualifying bean KafkaTemplate<String, FraudDecisionEvent>` | Add the `producer:` block under `spring.kafka` in fraud-service `application.yml`. |
| Compile error for `org.assertj` or `org.junit` | Add `spring-boot-starter-test` (test scope) to `fraud-service/pom.xml`. |
| `contextLoads` test fails | Run only the filtered tests with `-Dtest='*RuleTest,FraudEngineTest'`. |
| Scenario 2 never blocks | The commands were sent too slowly. Paste all four together. |
| Unexpected BLOCKED on a new scenario | The card was already used. Use a new cardId or run the reset command. |
| Nothing in the fraud log | Check `@Component` on `PaymentEventConsumer` and that Kafka shows `Up`. |
| `Node -1 disconnected` | Kafka is not healthy. Run `docker compose logs kafka --tail 30`. |
