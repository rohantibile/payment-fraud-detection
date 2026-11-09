# Verification Report

Checks the claims in the documentation set against the repo. Repo state: the zip attached on 2026-11-09 (after the GET, `static` and `@NotBlank` fixes).

Evidence tags: **[V]** confirmed by reading source or config. **[R]** reported by the project owner; the output was not shown to me. **[U]** still unverified.

Scope and limits: this is a static check. I read files and ran searches and diffs on the archive. I did **not** build the project, run the unit tests, start Docker or send any request. Runtime behavior is covered only by your own test documents (Days 1 to 4), which I did not witness.

---

## 1. Documents checked

`architecture.md`, `workflows.md`, `code-map.md`, `data-flow.md`, `project-map.md`, `memory-sheet.md`, `learning-plan.md`. All seven are present in the repo's `docs/` folder. Two of the copies in the repo were older than the ones I last delivered (see section 6).

---

## 2. Facts re-checked against source

| Claim | Where | Result |
|---|---|---|
| Velocity: 3 payments, 60 s window | `VelocityRule` | **[V]** `MAX_PAYMENTS = 3`, `WINDOW = 60 s` |
| Amount limit 100000, strictly greater | `AmountRule` | **[V]** `LIMIT = 100000`, `compareTo > 0` |
| Geo window 10 minutes, case-insensitive | `GeoMismatchRule` | **[V]** `WINDOW = 10 min`, `equalsIgnoreCase` |
| History look-back 10 minutes | `FraudProcessingService` | **[V]** `LOOKBACK = 10 min` |
| Retry: 3 retries, 1 s apart, then DLT | `KafkaErrorHandlingConfig` | **[V]** `FixedBackOff(1000L, 3L)` with `DeadLetterPublishingRecoverer` |
| Topics have 3 partitions, 1 replica | both `KafkaTopicConfig`, `KafkaErrorHandlingConfig` | **[V]** |
| Listeners: `payment-events` in fraud-service, `fraud-decisions` in payment-service | `@KafkaListener` search | **[V]** exactly two listeners |
| fraud-service uses manual acks | `application.yml` | **[V]** `enable-auto-commit: false`, `ack-mode: manual_immediate` |
| payment-service sets no commit mode | `application.yml` | **[V]** neither key present |
| `acks=all` on both producers | both `application.yml` | **[V]** |
| Ports 8081 and 8082 | both `application.yml` | **[V]** |
| Decision enum order APPROVED, FLAGGED, BLOCKED | `Decision.java` | **[V]** |
| PaymentStatus has PENDING first | `PaymentStatus.java` | **[V]** |
| Infra images | `docker-compose.yml` | **[V]** `apache/kafka:3.8.0`, `mysql:8.0`, `provectuslabs/kafka-ui:latest` |
| `reason` column length 500 | `TransactionRecord` | **[V]** |
| Test counts: Velocity 3, Amount 2, Geo 4, Engine 3 (12 in fraud-service); 2 decision tests in payment-service; 1 `contextLoads` in each | `@Test` count per file | **[V]** |
| Hardcoded DB password `root` | both `application.yml` | **[V]** |
| Java files | whole repo | **[V]** 39 files (31 main, 8 test), 890 lines. I had said 37 before; that was a miscount, now corrected in `project-map.md`. |

---

## 3. Fixes confirmed in the new zip

| Fix | File | Result |
|---|---|---|
| `@GetMapping("/{id}")` | `PaymentController.java` line 24 | **[V]** in source. Runtime behavior **[U]** until you run `curl` with a real id (expect 200) and an unknown id (expect 404). |
| `private final PaymentService service` | `FraudDecisionConsumer.java` | **[V]** |
| `@NotBlank` on `cardId`, `merchant`, `city` | `PaymentRequest.java` | **[V]** in source. Runtime behavior **[U]** until you POST an empty `city` (expect 400). |

A diff of the two zips shows these are the only three source files that changed.

---

## 4. Open items from earlier phases

| # | Item | Status |
|---|---|---|
| 8 | `frauddb.transactions` columns, index `idx_card_created`, `payments.status_reason` width | **[R]** You told me this is verified. I did not see the output, so the doc keeps the inferred column names. If you paste the output I will upgrade it to **[V]**. |
| 9 | payment-service `enable.auto.commit` in the startup log | **[R]** Same: reported verified, value not shown to me. The yml sets nothing, so the value comes from the Spring Kafka defaults. Please tell me the value for the maintenance guide. |
| 10 | Format of messages on `payment-events.DLT` | **[U]** |
| 11 | Malformed JSON behavior | **[U]** |
| 12 | Concurrent duplicates with the same `paymentId` | **[U]** by design (see `data-flow.md` section 7) |

---

## 5. Corrections made during verification

| Document | What was wrong | Fixed |
|---|---|---|
| `project-map.md` | Said the committed `day3-fraud-rules-test.md` was an older 10-test version. It already says 12 | Removed the claim and its housekeeping row |
| `project-map.md` | Said 37 Java files | Now 39 (31 main, 8 test) |
| `data-flow.md`, `memory-sheet.md`, `code-map.md` | Said a producer failure in payment-service is only logged. That holds for a failure reported by the send future; an exception thrown synchronously by `send` is not caught and fails the POST after the row is saved | Wording updated in all three. This also fits the Day 2 `500` you saw when Kafka was down. |

---

## 6. Documents that need re-copying into your repo

| File | Why |
|---|---|
| `docs/architecture.md` | The repo copy still has the older sentence "A failed send is only logged, it does not throw". The delivered version has the corrected wording. |
| `docs/project-map.md` | Repo copy has the wrong Day 3 claim and the 37 count. |
| `docs/code-map.md`, `docs/data-flow.md`, `docs/memory-sheet.md` | Producer-failure wording. |

The repo copies of `workflows.md` and `learning-plan.md` are identical to mine.

---

## 7. Findings still open in the code and repo

| Severity | Finding | File |
|---|---|---|
| Medium | Save then publish is not atomic (no outbox) | `PaymentService.create` |
| Medium | A synchronous send exception fails the POST after the row is saved; a failed async send leaves the payment PENDING | `PaymentEventProducer`, `PaymentService.create` |
| Medium | A payment whose event reaches the DLT stays PENDING; nothing reprocesses the DLT | design |
| Low | History query has no upper time bound | `FraudProcessingService` |
| Low | `provectuslabs/kafka-ui:latest` is not pinned and is unreliable in Codespaces | `docker-compose.yml` |
| Low | Day 1 test document still mentions Swagger UI (line 45) | `day1-infra-payment-crud-test.md` |
| Low | DB credentials hardcoded; `fraud.simulate-failure: true` committed | both `application.yml` |
| Info | The four `day*-test.md` files sit in the repo root | repo root |
| Info | No CI, Dockerfiles, `.devcontainer`, README content or demo GIF yet | Day 5 and 6 |

---

## 8. Overall result

The documentation matches the code. Every numeric and structural claim I re-checked was confirmed, and the three code fixes are present in the source. The checks I could not make are the ones that need a running system: the GET fix, the blank-string validation, the DLT message format and malformed-message handling. Run the three quick tests in section 3 and send me the results to close the first two.
