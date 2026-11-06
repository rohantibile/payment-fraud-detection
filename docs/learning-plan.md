# Learning Plan

A short plan to understand every part of this project well enough to explain it and change it. Each session is about 45 to 60 minutes. Use the Day 5 and Day 6 build days around it, not instead of it.

Documents used: `architecture.md`, `workflows.md`, `code-map.md`, `data-flow.md`, `project-map.md`, `memory-sheet.md`.

---

## Schedule

| Session | Topic | Read | Do | You can explain it when you can... |
|---|---|---|---|---|
| 1 | Big picture | `architecture.md`, memory sheet sections 1 to 3 | Say the 30-second pitch aloud without notes | Draw the two services, the topics and the databases on paper |
| 2 | The payment path | `workflows.md` W1, `code-map.md` section 3 | Open `PaymentController`, `PaymentService.create`, `PaymentEventProducer` | Say why save and publish are two steps and what that risks |
| 3 | The fraud core | `code-map.md` section 4 | Read `FraudProcessingService.process` line by line | Explain each of the 6 steps and why publish comes before save |
| 4 | The rules | `FraudEngine`, three rule classes, their tests | Change `MAX_PAYMENTS` to 2, run the tests, read the failure, undo | Predict the verdict for any (history, event) pair |
| 5 | Kafka concepts | `data-flow.md` section 4 | Consume `fraud-decisions` from the terminal and find the partition of one card | Explain key, partition, offset, consumer group |
| 6 | Reliability | `workflows.md` W2 to W4, `data-flow.md` section 7 | Run the failure demo (`SIMULATE_FAILURE` merchant) and find the DLT message | Walk through retry, DLT and idempotency without notes |
| 7 | Data and states | `data-flow.md` sections 3 and 5 | Run the SQL queries in section 8 | Name every state a payment can be in and how it leaves it |
| 8 | Weaknesses and fixes | memory sheet section 5 | Sketch an outbox table on paper | Explain the outbox pattern in 60 seconds |
| 9 | Mock interview | memory sheet section 7 | Answer every question aloud, then check it | Answer each one in under a minute |

You may merge sessions 1 to 3 into one if you already know the code from building it.

---

## Hands-on experiments (each takes under 10 minutes)

| # | Experiment | What you learn |
|---|---|---|
| 1 | Send 4 payments for one card in a loop, then wait 65 s and send a fifth | Velocity window |
| 2 | Send the same card from Pune then Mumbai | Geo rule |
| 3 | Stop fraud-service, send 3 payments, start it again | Backlog and recovery |
| 4 | Send a payment with merchant `SIMULATE_FAILURE` | Retry then the DLT |
| 5 | Add a log line in `FraudEngine.evaluate` that prints every triggered rule | How rules combine |
| 6 | Add a new rule class `NightTimeRule` (not committed, only for learning) | How little the engine needs to change |

---

## Self-check questions per session

1. Which class receives the HTTP request, and what does it call?
2. What status does a new payment have, and who changes it?
3. Why is `cardId` the Kafka key?
4. When does `ack.acknowledge()` run, and what if `process` throws?
5. What is checked first in `process`, and why?
6. What does the engine do when two rules trigger?
7. Which rules produce BLOCKED, and which produce FLAGGED?
8. What happens after the 4th failed attempt?
9. Why does a duplicate verdict not break a payment?
10. Name two weaknesses and their fixes.

Answers are in `memory-sheet.md`. A quiz follows this phase, one question at a time.

---

## Progress tracker

Mark each as you finish it.

- [ ] Session 1  - [ ] Session 2  - [ ] Session 3
- [ ] Session 4  - [ ] Session 5  - [ ] Session 6
- [ ] Session 7  - [ ] Session 8  - [ ] Session 9

---

## Before a real interview

1. Re-read `memory-sheet.md` (10 minutes).
2. Run the full demo once end to end.
3. Be ready to open `FraudProcessingService.process` and explain it aloud.
4. Know the three weaknesses and their fixes cold.
