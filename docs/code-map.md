# Code Map

Per-file guide to every Java source file in both services. Source of truth: the repo zip you attached (branch state after the Day 4 merge). Nothing here comes from memory of earlier chats.

Evidence tags: **[V]** read in the source. **[I]** inferred from framework behavior. **[U]** unverified, needs a run to confirm. **[T]** a unit test exists.

Paths: `PS` = `payment-service/src/main/java/com/rohan/payment`, `FS` = `fraud-service/src/main/java/com/rohan/fraud`.

---

## 1. How to read the code (suggested order)

1. `PS/controller/PaymentController` (entry point)
2. `PS/service/PaymentService.create` (save, then publish)
3. `PS/kafka/PaymentEventProducer`
4. `FS/kafka/PaymentEventConsumer`
5. `FS/service/FraudProcessingService.process` (the core of the system)
6. `FS/engine/FraudEngine` and the three rules in `FS/rules/`
7. `FS/kafka/FraudDecisionProducer`
8. `PS/kafka/FraudDecisionConsumer`, then `PS/service/PaymentService.applyDecision`

That is the exact order a payment travels through the system.

---

## 2. Layers at a glance

| Layer | payment-service | fraud-service |
|---|---|---|
| Entry | `PaymentController` (REST) | `PaymentEventConsumer` (Kafka) |
| Business logic | `PaymentService` | `FraudProcessingService`, `FraudEngine`, 3 rules |
| Messaging out | `PaymentEventProducer` | `FraudDecisionProducer` |
| Messaging in | `FraudDecisionConsumer` | `PaymentEventConsumer` |
| Persistence | `Payment`, `PaymentRepository` (`paymentdb`) | `TransactionRecord`, `TransactionRepository` (`frauddb`) |
| Contracts (DTOs) | `PaymentRequest`, `PaymentResponse`, `PaymentEvent`, `FraudDecisionEvent` | `PaymentEvent`, `FraudDecisionEvent` |
| Config | `KafkaTopicConfig` | `KafkaTopicConfig`, `KafkaErrorHandlingConfig` |

The two services share no code. Each has its own copy of `PaymentEvent` and `FraudDecisionEvent`. The JSON field names are the contract between them. **[V]**

---

## 3. payment-service

### 3.1 `PaymentServiceApplication` (13 lines)
`@SpringBootApplication` main class. Nothing else. **[V]**

### 3.2 `controller/PaymentController` (27 lines)
- Base path `/api/v1/payments`.
- `create(@Valid @RequestBody PaymentRequest)`: `POST`, returns `201`, delegates to `service.create`. **[V]**
- `get(@PathVariable String id)`: delegates to `service.get`. **[V]**
- **BUG (line 24): `@GetMapping` has no `"/{id}"`.** The method expects a path variable the mapping never declares, so `GET /api/v1/payments/{id}` has no matching handler. Fix: `@GetMapping("/{id}")`. **[V]** This zip still contains the bug; you said you are applying the fix, and I will re-check it in Phase 7.
- Where the 404 comes from: `PaymentService.get` throws `ResponseStatusException`, so there is no custom exception handler class in the repo. **[V]**

### 3.3 `service/PaymentService` (60 lines)
| Method | What it does | Notes |
|---|---|---|
| `create(PaymentRequest)` | Builds a `Payment`, `repository.save`, then `producer.publish(PaymentEvent.from(saved))`, returns `PaymentResponse` | Save and publish are two separate steps. If the app dies between them, the payment stays PENDING forever and no event exists. Fix = transactional outbox. **[V]** |
| `get(String id)` | `findById`, mapped to `PaymentResponse`, else `ResponseStatusException(404)` | **[V]** |
| `applyDecision(FraudDecisionEvent)` `@Transactional` | Loads payment. Unknown id: warn and return. Status not PENDING: log and ignore (idempotency). Otherwise truncate reason to 250 chars, `PaymentStatus.valueOf(decision)`, set status and reason | There is no explicit `save`; JPA dirty checking flushes at commit. **[V]** `[I]` Unit tests: `PaymentServiceDecisionTest` (2). **[T]** |

Risk: `PaymentStatus.valueOf(event.decision())` throws `IllegalArgumentException` for any string outside PENDING / APPROVED / FLAGGED / BLOCKED. The fraud side only sends APPROVED / FLAGGED / BLOCKED, so it is safe today. **[V]** payment-service has no error handler or DLT, so a poison message behavior is **[U]**.

### 3.4 `kafka/PaymentEventProducer` (34 lines)
- `publish(PaymentEvent)`: `kafkaTemplate.send("payment-events", cardId, event)`. The key is `cardId`, so one card always lands on one partition. **[V]**
- Asynchronous. `whenComplete` only logs. If the send fails, the failure is logged and nothing else happens: the HTTP call has already returned 201. **[V]**

### 3.5 `kafka/FraudDecisionConsumer` (27 lines)
- `@KafkaListener(topics = "fraud-decisions")` calls `service.applyDecision(event)`. **[V]**
- **Smell (line 16): `private static PaymentService service`** assigned from the constructor. A static field set per instance works for one bean but is wrong style. It should be `private final`. Does not break behavior. **[V]**
- No `Acknowledgment`, so the commit mode follows Spring Kafka defaults. The `application.yml` of this service sets no `enable-auto-commit` and no `ack-mode`. **[V]** Which one is actually active is **[U]**: check `enable.auto.commit` in the startup `ConsumerConfig values` log.

### 3.6 `entity/Payment` (41 lines) and `PaymentStatus` (2 lines)
- Table `payments`. Id is a random UUID string created in the field initializer. **[V]**
- Defaults at construction: `status = PENDING`, `createdAt = Instant.now()`. **[V]**
- Columns: `cardId`, `amount(15,2)`, `merchant`, `city` all non-null; `statusReason` nullable. **[V]**
- `PaymentStatus`: `PENDING, APPROVED, FLAGGED, BLOCKED`. Stored as a string (`@Enumerated(STRING)`). **[V]**

### 3.7 `repository/PaymentRepository` (6 lines)
`JpaRepository<Payment, String>` with no custom queries. **[V]**

### 3.8 DTOs (`dto/`)
| File | Shape | Notes |
|---|---|---|
| `PaymentRequest` | `cardId, amount, merchant, city` | `@NotNull` on all four, `@Positive` on amount. A blank string `""` passes (`@NotBlank` is not used). **[V]** |
| `PaymentResponse` | `id, cardId, amount, merchant, city, status, statusReason, createdAt` | `from(Payment)` factory. **[V]** |
| `PaymentEvent` | `paymentId, cardId, amount, merchant, city, createdAt` | `from(Payment)` factory. Goes to `payment-events`. **[V]** |
| `FraudDecisionEvent` | `paymentId, cardId, decision (String), reason, decidedAt` | Decision is a `String` here, an enum in fraud-service. **[V]** |

### 3.9 `config/KafkaTopicConfig` (20 lines)
Declares `payment-events` and `fraud-decisions`, both 3 partitions, 1 replica. Spring's `KafkaAdmin` creates them at startup if missing. `[I]` Fraud-service declares `fraud-decisions` too with identical settings, so the duplicate is harmless. **[V]**

---

## 4. fraud-service

### 4.1 `FraudServiceApplication` (13 lines)
`@SpringBootApplication` main class. **[V]**

### 4.2 `kafka/PaymentEventConsumer` (35 lines)
- `@KafkaListener(topics = "payment-events")` with headers `RECEIVED_KEY`, `RECEIVED_PARTITION`, `OFFSET` and an `Acknowledgment`. **[V]**
- Order: log, `service.process(event)`, `ack.acknowledge()`. The ack happens only after success. If `process` throws, there is no ack and the error handler takes over. **[V]**
- Commit mode: `enable-auto-commit: false` plus `ack-mode: manual_immediate` in `application.yml`. **[V]**

### 4.3 `service/FraudProcessingService` (62 lines): the core
`process(PaymentEvent)` runs these steps in order **[V]**:

| # | Step | Why |
|---|---|---|
| 1 | `repository.existsById(paymentId)`: if true, log "Duplicate ... skipped" and return | Idempotency. A replayed event does nothing. |
| 2 | If `simulateFailure && merchant == "SIMULATE_FAILURE"` throw `IllegalStateException` | Demo hook for the retry/DLT flow. Property `fraud.simulate-failure` (default false; the committed yml sets true). |
| 3 | `findByCardIdAndCreatedAtGreaterThanEqual(cardId, createdAt - 10 min)` | Loads the card's recent history. 10 minutes = the longest rule window (geo). |
| 4 | `engine.evaluate(event, history)` | Produces a `FraudVerdict`. |
| 5 | `producer.publish(new FraudDecisionEvent(...))` | Publish the verdict first. |
| 6 | `repository.save(new TransactionRecord(...))` | Save after publishing. |

Design trade-off, stated in the code comment: publish before save. If publish fails, nothing is saved and the retry is clean. If the save fails after a successful publish, the retry republishes the same verdict, which payment-service ignores because the payment is no longer PENDING. This is at-least-once delivery made safe by idempotency on both sides. **[V]**

Gaps: the history query has only a lower time bound, so records with a `createdAt` after the event also count. The history only includes payments that already have a stored verdict. **[V]**

### 4.4 `engine/FraudEngine` (40 lines) and `FraudVerdict` (4 lines)
- Constructor takes `List<FraudRule>`. Spring injects every `FraudRule` bean, so a new rule needs no edit here. **[V]**
- `evaluate`: runs every rule, keeps results that are not APPROVED. None: `APPROVED, "No rules triggered"`. Otherwise the worst decision wins via `max(naturalOrder())`, which relies on the enum order `APPROVED < FLAGGED < BLOCKED`. Reasons are joined as `RULE: reason; RULE: reason`. **[V]** **[T]** (3 engine tests)
- Warning: reordering the `Decision` enum silently changes which verdict wins. **[V]**

### 4.5 Rules (`rules/`)
| File | Trigger | Result | Constants | Tests |
|---|---|---|---|---|
| `FraudRule` | Interface: `RuleResult evaluate(PaymentEvent, List<TransactionRecord>)` | | | |
| `VelocityRule` | At least 3 history records with `createdAt >= event.createdAt - 60s` | BLOCKED, reason "`N` payments in the last 60 seconds" | `MAX_PAYMENTS=3`, `WINDOW=60s` | **[T]** 3 |
| `AmountRule` | `amount > 100000` (strictly greater) | FLAGGED, reason "Amount X exceeds 100000" | `LIMIT=100000` | **[T]** 2 |
| `GeoMismatchRule` | Any history record within 10 minutes with a different city (`equalsIgnoreCase`) | BLOCKED, reason names both cities | `WINDOW=10min` | **[T]** 4 |
| `RuleResult` | `record(rule, decision, reason)` with `pass(rule)` factory returning APPROVED | | | |

All three rules are stateless `@Component`s and pure functions of (event, history), which is why they are easy to unit test without Kafka or a database. **[V]**

The velocity rule counts a payment from any city. The geo rule uses `findFirst`, so the reason mentions the first mismatching record found, not necessarily the newest. **[V]**

### 4.6 `kafka/FraudDecisionProducer` (38 lines)
- `publish(FraudDecisionEvent)`: sends to `fraud-decisions` with key `cardId`, then **blocks** with `.get(10, SECONDS)`. **[V]**
- On timeout, execution failure or interrupt it throws `IllegalStateException`. That exception is what triggers the retry and DLT path. Unlike payment-service, the send here is synchronous on purpose. **[V]**

### 4.7 `config/KafkaErrorHandlingConfig` (25 lines)
- Declares topic `payment-events.DLT` (3 partitions; must be at least as many as `payment-events`). **[V]**
- `DefaultErrorHandler` with a `DeadLetterPublishingRecoverer` and `FixedBackOff(1000L, 3L)`: 1 second between attempts, 3 retries, then the record goes to the DLT and the consumer moves on. **[V]**
- That the handler is applied to the listener container is `[I]` (Spring Boot auto-wires a single `CommonErrorHandler` bean into the container factory). It matches the Day 4 behavior you tested.

### 4.8 `config/KafkaTopicConfig` (16 lines)
Declares `fraud-decisions`, 3 partitions, 1 replica. **[V]**

### 4.9 `model/TransactionRecord` (39 lines) and `Decision` (2 lines)
- Table `transactions`; id is the **paymentId** (a business key, not generated), which is what makes `existsById` an idempotency check. **[V]**
- Columns: `cardId`, `amount(15,2)`, `city`, `createdAt`, `decision` (STRING), `reason` (`length=500`). **[V]**
- Index `idx_card_created` on `(cardId, createdAt)` is declared in the annotation. That it exists in MySQL is **[U]**. Check with `SHOW INDEX FROM frauddb.transactions;`. Note `columnList` uses field names, which Hibernate may or may not map to `card_id`, `created_at`. **[U]**
- `Decision`: `APPROVED, FLAGGED, BLOCKED`. Order matters (see 4.4). **[V]**
- Day 3 bug history: `getCity()` once returned `paymentId`. Now `return city;`. **[V]**

### 4.10 `repository/TransactionRepository` (10 lines)
One derived query: `findByCardIdAndCreatedAtGreaterThanEqual(cardId, since)`. **[V]**

### 4.11 DTOs
| File | Shape |
|---|---|
| `PaymentEvent` | `paymentId, cardId, amount, merchant, city, createdAt` (same fields as payment-service) |
| `FraudDecisionEvent` | `paymentId, cardId, Decision decision, reason, decidedAt` |

---

## 5. Tests

| File | Tests | Covers |
|---|---|---|
| `VelocityRuleTest` | 3 | under limit, at limit, old payments |
| `AmountRuleTest` | 2 | exactly 100000 approved, just above flagged |
| `GeoMismatchRuleTest` | 4 | same city, case-insensitive, 5 min different city, 11 min different city |
| `FraudEngineTest` | 3 | normal, high amount, amount plus geo |
| `TestData` | helper | builds events and records |
| `PaymentServiceDecisionTest` | 2 | `applyDecision` behavior |
| `*ApplicationTests` (both) | 1 each | `contextLoads`, needs MySQL and Kafka running |

Not tested anywhere: `FraudProcessingService` (idempotency, ordering of publish/save), both Kafka consumers/producers, the controller, validation, and the retry/DLT flow. Those were only tested by hand through the `.md` test documents. **[V]**

I have not run any tests in this session; the archive is untrusted and counts above come from reading the files. 12 fraud tests were also confirmed by your Day 3 run.

---

## 6. Key identifiers (grep cheat sheet)

| Looking for | Search for |
|---|---|
| Topic names | `payment-events`, `fraud-decisions`, `payment-events.DLT` |
| Thresholds | `MAX_PAYMENTS`, `LIMIT`, `WINDOW`, `LOOKBACK` |
| Demo failure hook | `SIMULATE_FAILURE`, `fraud.simulate-failure` |
| Retry settings | `FixedBackOff` |
| Idempotency | `existsById`, `already` (payment-service log text) |
| Ports | `8081` (payment), `8082` (fraud) in each `application.yml` |

---

## 7. Findings from this phase

| # | Finding | Severity | Status |
|---|---|---|---|
| 1 | `@GetMapping` missing `"/{id}"` | High (feature broken) | You are fixing; re-verify Phase 7 |
| 2 | `private static PaymentService service` in `FraudDecisionConsumer` | Low (style) | Change to `private final` |
| 3 | Save then publish is not atomic (no outbox) | Medium (design) | Interview talking point; fix optional |
| 4 | Producer failure in payment-service is only logged | Medium | Same root as #3 |
| 5 | History query has no upper time bound | Low | Note only |
| 6 | `PaymentRequest` accepts blank strings | Low | `@NotBlank` would fix |
| 7 | Index `idx_card_created` creation unconfirmed | Low | Run `SHOW INDEX` |
| 8 | Payment-service offset commit mode and poison-message behavior unconfirmed | Medium | Check startup log, then test |
| 9 | Committed `fraud.simulate-failure: true` | Low | Fine for demo; mention in README |
