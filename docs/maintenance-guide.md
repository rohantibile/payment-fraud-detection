# Maintenance Guide

How to keep the documentation set true as the code changes, and how to make common changes safely. Based on the repo as verified in `verification-report.md`.

Evidence tags: **[V]** confirmed in source. **[I]** inferred. **[U]** unverified.

---

## 1. The documentation set

| File | Describes | Update when you change... |
|---|---|---|
| `architecture.md` | Components, startup, dependencies, integrations | Services, infra, ports, a new integration |
| `workflows.md` | Request and event paths, retry, failure handling | Consumer logic, error handling, endpoints |
| `code-map.md` | Per-file guide, findings table | Any class: added, renamed, removed or changed behavior |
| `data-flow.md` | Tables, topics, message JSON, status lifecycle | Entities, DTO fields, topics, serializers, statuses |
| `project-map.md` | Repo tree, build, ports, run commands | Folder layout, dependencies, ports, scripts |
| `memory-sheet.md` | Recall sheet: numbers, decisions, weaknesses | Thresholds, test counts, a weakness fixed, a new bug story |
| `learning-plan.md` | Study sessions | Rarely: only when whole areas are added |
| `verification-report.md` | What was checked and how | Re-run after each batch of changes |

Rule of thumb: **change code, then update the docs in the same pull request.** A stale doc is worse than none, because the reader trusts it.

---

## 2. Change checklists

### Change a fraud threshold (60 s, 3 payments, 100000, 10 min)
1. Edit the constant in `fraud-service/.../rules/<Rule>.java`.
2. If you raise the geo window above 10 minutes, also raise `LOOKBACK` in `FraudProcessingService`, or the rule will not see old enough history. **[V]**
3. Update the matching unit test in `fraud-service/src/test`.
4. Docs: `memory-sheet.md` section 2, `code-map.md` section 4.5, and the Day 3 test document.

### Add a fraud rule
1. Create `@Component public class NewRule implements FraudRule` in `fraud-service/.../rules/`. The engine collects all rule beans, so no other class needs editing. **[V]**
2. Return `new RuleResult(NAME, Decision.X, reason)` or `RuleResult.pass(NAME)`.
3. Add a `NewRuleTest` using `TestData`.
4. If the rule needs history older than 10 minutes, change `LOOKBACK`.
5. Docs: `code-map.md` (rules table), `workflows.md` (rule list), `memory-sheet.md`, `data-flow.md` only if you add stored fields.

### Add a field to a payment
1. `Payment` entity, `PaymentRequest`, `PaymentResponse`, `PaymentEvent` in payment-service.
2. The same field in fraud-service's `PaymentEvent` record. The two copies must match by JSON name. **[V]**
3. Hibernate `ddl-auto: update` adds the column to an existing table. It does not remove or rename columns. **[I]**
4. Docs: `data-flow.md` (schema, message example), `code-map.md`.

### Change a Kafka topic, partition count or serializer
1. Update both `KafkaTopicConfig` classes if the topic is declared in both (`fraud-decisions` is). **[V]**
2. An existing topic is not altered by `NewTopic` when the partition count changes. Delete the topic or the volume (`docker compose down -v`) in dev. **[I]**
3. More partitions changes which partition a key maps to, so ordering across the change is not guaranteed. **[I]**
4. Keep `payment-events.DLT` partitions at least equal to `payment-events`. **[V]** (comment in `KafkaErrorHandlingConfig`)
5. Docs: `data-flow.md` section 4, `architecture.md`.

### Change retry behavior
Edit `FixedBackOff(1000L, 3L)` in `KafkaErrorHandlingConfig`. Docs: `workflows.md` (retry diagram and failure table), `memory-sheet.md`.

### Change the status values
`PaymentStatus` (payment-service) and `Decision` (fraud-service) are separate enums linked by name: payment-service calls `PaymentStatus.valueOf(decision)`. A decision value missing from `PaymentStatus` throws. **[V]** Also, `FraudEngine` relies on the `Decision` order to pick the worst result. **[V]** Update `data-flow.md` section 5.

### Change the database or credentials
Both `application.yml` files. Do not commit real credentials. Docs: `project-map.md` section 4a.

---

## 3. When a fix is planned: what the docs should say afterwards

| Planned fix | Code change | Docs to update |
|---|---|---|
| Transactional outbox | New outbox table and entity in payment-service, relay that publishes it, `create` writes payment and outbox row in one transaction | `code-map.md` (3.3, 3.4, findings), `data-flow.md` (schema, section 7), `workflows.md` (W1), `memory-sheet.md` section 5 (remove weakness 1 and 2) |
| DLT reprocessing | New consumer or endpoint for `payment-events.DLT` | `workflows.md` (W2), `data-flow.md`, `memory-sheet.md` |
| Upper bound on history query | New repository method plus a call change in `FraudProcessingService` | `code-map.md`, `memory-sheet.md` |
| Env-var credentials | `application.yml` placeholders, README run instructions | `project-map.md`, `architecture.md` |
| Dockerfiles and services in compose | New files plus `docker-compose.yml` | `project-map.md` (tree, run section), `architecture.md` (diagrams A and B), README |
| Pin Kafka UI version | `docker-compose.yml` | `verification-report.md` finding |

---

## 4. Regenerating the diagrams

All diagrams are Mermaid blocks inside the markdown files. GitHub renders them directly. To check syntax before committing:

```bash
npx -y @mermaid-js/mermaid-cli -i diagram.mmd -o diagram.svg
```
A syntax error produces a message and no file. Edit the block in the `.md`, not a separate image, so the text stays the source of truth.

---

## 5. Quick re-verification (about 10 minutes)

Run after any code change that touches behavior.

```bash
# 1. Unit tests, no Kafka or database needed
cd fraud-service && ./mvnw test -Dtest='*RuleTest,FraudEngineTest'
# expect 12 tests, 0 failures

# 2. Infra
docker compose up -d && sleep 25 && docker compose ps

# 3. Services (two terminals)
cd fraud-service && ./mvnw spring-boot:run
cd payment-service && ./mvnw spring-boot:run

# 4. Happy path
curl -s -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-M1","amount":100,"merchant":"Test","city":"Pune"}'
# take the id, wait 2 s, then:
curl -s http://localhost:8081/api/v1/payments/<ID>      # expect APPROVED

# 5. Validation and not found
curl -i -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-M1","amount":100,"merchant":"Test","city":""}'    # expect 400
curl -i http://localhost:8081/api/v1/payments/does-not-exist           # expect 404
```
For the full scenario list use the Day 3 and Day 4 test documents. Expected results in those documents are the reference.

---

## 6. Troubleshooting table

| Symptom | Likely cause | Check |
|---|---|---|
| `500` on POST, "Node -1 disconnected" | Kafka not healthy | `docker compose logs kafka --tail 30` |
| Nothing in the fraud-service log | Consumer class missing `@Component`, or Kafka down | Class annotations; `docker compose ps` |
| Payment stays PENDING | fraud-service down, event in the DLT, or verdict not yet consumed | fraud-service log; consume `payment-events.DLT` |
| Unexpected BLOCKED on a new test | The card id was used before; history is per card | Use a new `cardId` or `TRUNCATE frauddb.transactions;` |
| `GET` by id returns an error | Mapping or id problem | Confirm `@GetMapping("/{id}")` is present **[V]** |
| `Communications link failure` | MySQL still starting | Wait 20 s |
| Kafka UI offline or topics loading forever | Old Kafka client in the `provectuslabs` image | Use the terminal `kafka-console-consumer.sh` commands |
| Unit test `contextLoads` fails | Needs MySQL and Kafka running | Run the filtered unit tests instead |
| Serializer class not found | Spring Boot 4 uses Jackson 3 | Use `JacksonJsonSerializer` and `JacksonJsonDeserializer` |

---

## 7. Release and hygiene checklist

Before a public push or a recruiter-facing link:

- [ ] No real secrets in any `application.yml`; credentials from environment variables
- [ ] README has no placeholder text and the diagram shows KRaft (no Zookeeper)
- [ ] Day 1 test document: Swagger line removed
- [ ] `docs/` contains all eight files, and each matches the current code
- [ ] CI workflow passes on the default branch
- [ ] `fraud.simulate-failure` is documented in the README as a demo switch
- [ ] Kafka UI image tag pinned, or noted as optional
- [ ] A demo GIF or screenshots linked from the README

---

## 8. Keeping your own understanding fresh

1. Read `memory-sheet.md` before any interview or code review.
2. After each change, re-read the one doc it touches and fix any sentence that is no longer true.
3. Re-run `verification-report.md`'s checks (section 2 there) when many files changed. A search for the constants and the `@KafkaListener` annotations takes a minute and catches most drift.
4. Anything you could not run stays marked **[U]**. Do not turn it into a claim until you have seen it work.

---

## 9. Open items to carry forward

| # | Item | Status |
|---|---|---|
| 1 | Runtime check of the GET fix and blank-string validation | **[U]** |
| 2 | `frauddb.transactions` columns and the `idx_card_created` index | **[R]** reported checked, output not shown to me |
| 3 | payment-service `enable.auto.commit` value | **[R]** reported checked, value not recorded |
| 4 | `payment-events.DLT` message format | **[U]** |
| 5 | Malformed JSON handling | **[U]** |
| 6 | Re-copy the corrected docs into `docs/` | Pending on your side |
