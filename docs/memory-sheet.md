# Memory Sheet

A one-page recall sheet for the Payment Fraud Detection project. Read it before an interview or after a break. Every fact below comes from the repo (see `code-map.md` and `data-flow.md` for the detail).

---

## 1. The 30-second pitch

> "I built a real-time payment fraud detection system with two Spring Boot microservices connected by Kafka. When a payment comes in, payment-service saves it as PENDING and publishes an event. fraud-service consumes it, runs it through three fraud rules, and publishes a verdict. payment-service then updates the payment to APPROVED, FLAGGED or BLOCKED. I used manual acknowledgements, idempotent consumers, and a retry plus dead-letter topic so failures don't lose or duplicate payments."

---

## 2. Numbers to remember

| Fact | Value |
|---|---|
| Services / ports | payment-service 8081, fraud-service 8082 |
| Spring Boot / Java | 4.1.1 / 17 |
| Kafka | 3.8.0, KRaft mode, no Zookeeper |
| Database | MySQL 8.0, two DBs: `paymentdb`, `frauddb` |
| Topics | `payment-events`, `fraud-decisions`, `payment-events.DLT` (3 partitions each) |
| Kafka key | `cardId` |
| Consumer groups | `fraud-service-group`, `payment-service-group` |
| Velocity rule | 3 or more earlier payments in 60 s, so the 4th is BLOCKED |
| Amount rule | greater than 100000, FLAGGED (exactly 100000 is fine) |
| Geo rule | different city within 10 min, BLOCKED (case-insensitive) |
| Retries | 3 retries, 1 s apart, then the DLT |
| Producer ack | `acks=all` |
| Unit tests | 12 in fraud-service (3 + 2 + 4 + 3) and 2 in payment-service |

---

## 3. The flow in six lines

1. `POST /api/v1/payments` saves a `PENDING` row and publishes `PaymentEvent` (key = cardId).
2. fraud-service skips it if the paymentId already exists.
3. It loads the card's last 10 minutes of history and runs all rules.
4. The worst result wins: BLOCKED over FLAGGED over APPROVED. All reasons are joined.
5. It publishes the verdict first, then saves the record.
6. payment-service updates the status only if it is still PENDING.

---

## 4. Design decisions and the reason for each

| Decision | Why |
|---|---|
| Kafka between the services | The payment request does not wait for fraud checking; the services fail and scale separately |
| Key = `cardId` | One card always goes to one partition, so its payments are handled in order. Velocity and geo rules need that. |
| Manual ack after processing (fraud-service) | A crash mid-processing means the message is delivered again instead of lost |
| `existsById` check | Redelivery is safe because duplicates are skipped |
| "Only if PENDING" check (payment-service) | A repeated verdict cannot overwrite a final status |
| Publish, then save (fraud-service) | Failed publish leaves nothing saved, so the retry starts clean. Failed save causes a repeat publish, which is ignored downstream. |
| `FraudRule` interface plus `List<FraudRule>` | A new rule is one new class; the engine needs no change (Strategy pattern) |
| Retry then DLT | A bad message cannot block the partition forever |
| Separate DTO copies per service | No shared code, so the JSON is the only contract |
| Type headers off, fixed default type | Services do not depend on each other's Java class names |
| KRaft instead of Zookeeper | Fewer containers, and it fixed the Day 2 crash loop |
| Decision enum order APPROVED, FLAGGED, BLOCKED | The engine picks the worst with `max`, which depends on that order |

---

## 5. Known weaknesses (say them first, before the interviewer does)

1. **Save then publish is not atomic** in payment-service. A crash between them leaves a PENDING payment with no event. Fix: transactional outbox.
2. **Producer failure is only logged** in payment-service; the client still gets 201.
3. **PENDING has no timeout**: a payment whose event reaches the DLT stays PENDING, and nothing reprocesses the DLT.
4. **`GET /api/v1/payments/{id}` mapping bug** (`@GetMapping` without `"/{id}"`), found in review, fix in progress.
5. **`existsById` is not atomic**, so two simultaneous duplicates could slip through. Per-card ordering makes this unlikely.
6. **Rules read a time window with only a lower bound** and see only payments that already have a verdict.
7. **Credentials are hardcoded** (local demo only).
8. Only the rules, the engine and `applyDecision` have unit tests; the consumers, producers and retry flow were tested by hand.

Interview tip: naming weaknesses 1 to 3 together with their fixes shows you understand distributed-systems trade-offs.

---

## 6. Bugs you actually hit (great interview stories)

| Day | Symptom | Cause | Fix |
|---|---|---|---|
| 2 | `500` on POST, "Node -1 disconnected" | Kafka in Zookeeper mode crash-looped | Switched to KRaft `apache/kafka:3.8.0` |
| 2 | Consumer never logged | `@Component` missing on the consumer class | Added it |
| 2 | Serializer class not found | Spring Boot 4 uses Jackson 3 | Used `JacksonJsonSerializer` / `JacksonJsonDeserializer` |
| 3 | 2 geo tests failed | `getCity()` returned `paymentId` | Return `city` |
| 3 | Reason too long for the column | Reasons are joined from several rules | `@Column(length = 500)` |
| 3 | Kafka UI offline, topics page loading forever | Old Kafka client in the UI image against the broker | Used terminal consumer commands instead |
| Review | GET by id has no handler | `@GetMapping` missing `"/{id}"` | Add the path |

---

## 7. Likely interview questions with short answers

**Why Kafka and not REST between the services?** Decoupling and buffering: payments are accepted even if fraud-service is down, and the backlog is processed when it returns.

**What if fraud-service is down?** Payments stay PENDING and events wait in `payment-events`. On restart the consumer continues from its committed offset.

**What if a message is delivered twice?** fraud-service skips known paymentIds; payment-service ignores verdicts for non-PENDING payments.

**How do you keep one card's events in order?** Use `cardId` as the Kafka key so they share a partition.

**What happens to a message that keeps failing?** 3 retries 1 s apart, then it is sent to `payment-events.DLT` and the consumer moves on.

**Exactly-once?** No: at-least-once delivery plus idempotent consumers, which gives the same visible result.

**How would you add a fourth rule?** Add a `@Component` implementing `FraudRule`; the engine collects all rule beans automatically.

**What would you improve?** Outbox pattern, a reprocessing path for the DLT, an upper time bound in the history query, tests for the consumers with embedded Kafka, and moving credentials to environment variables.

---

## 8. Commands you should know by heart

```bash
docker compose up -d && docker compose ps
./mvnw spring-boot:run                 # inside a service folder
./mvnw test -Dtest='*RuleTest,FraudEngineTest'
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e "SELECT ..."
docker compose logs kafka --tail 30
docker compose down -v                 # wipes the data
```

---

## 9. Things I am not sure about (do not claim these in an interview)

- Whether payment-service commits offsets automatically (check `enable.auto.commit` in its startup log).
- What happens to a malformed JSON message (no test).
- Whether the `idx_card_created` index exists in MySQL.
- The exact key and value format on `payment-events.DLT`.
