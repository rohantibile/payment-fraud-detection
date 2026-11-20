# Day 5 Test: Unit Tests and CI

Goal: all unit tests pass locally in Codespaces, and the GitHub Actions workflow runs them and turns green.

Tester: ______  Date: ______  Branch: `feature/day5-tests-ci`  Environment: Codespaces

## A. Add the files

Create a branch, then copy the five files to these paths:

```bash
git checkout main && git pull
git checkout -b feature/day5-tests-ci
```

| File | Path in the repo |
|---|---|
| `ci.yml` | `.github/workflows/ci.yml` |
| `FraudProcessingServiceTest.java` | `fraud-service/src/test/java/com/rohan/fraud/service/` |
| `RuleBoundaryTest.java` | `fraud-service/src/test/java/com/rohan/fraud/rules/` |
| `PaymentServiceTest.java` | `payment-service/src/test/java/com/rohan/payment/service/` |
| `PaymentControllerTest.java` | `payment-service/src/test/java/com/rohan/payment/controller/` |
| `day5-tests-ci-test.md` (this document) | repo root, next to the `day1` to `day4` test documents |

No existing file is changed. No Docker, Kafka or MySQL is needed for these tests.

## B. Run the unit tests

```bash
cd fraud-service
./mvnw test -Dtest='!*ApplicationTests'
cd ../payment-service
./mvnw test -Dtest='!*ApplicationTests'
```

Expected:

| Service | Test class | Tests |
|---|---|---|
| fraud-service | VelocityRuleTest | 3 |
| fraud-service | AmountRuleTest | 2 |
| fraud-service | GeoMismatchRuleTest | 4 |
| fraud-service | FraudEngineTest | 3 |
| fraud-service | RuleBoundaryTest (new) | 6 |
| fraud-service | FraudProcessingServiceTest (new) | 7 |
| payment-service | PaymentServiceDecisionTest | 2 |
| payment-service | PaymentServiceTest (new) | 6 |
| payment-service | PaymentControllerTest (new) | 6 |

Totals: fraud-service `Tests run: 25`, payment-service `Tests run: 14`, all with `Failures: 0, Errors: 0`.

If a test fails, copy the first error block (the lines starting with `[ERROR]`) and send it.

## C. Check the GET fix and validation through the new controller tests

| Check | Test that covers it | Pass / Fail |
|---|---|---|
| POST returns 201 | `validPostReturns201WithPendingStatus` | |
| Blank city returns 400 | `blankCityReturns400` | |
| Negative amount returns 400 | `negativeAmountReturns400` | |
| Missing field returns 400 | `missingFieldReturns400` | |
| GET by id returns 200 | `getByIdReturns200` | |
| Unknown id returns 404 | `getUnknownIdReturns404` | |

## D. Run the real application once (optional, 5 minutes)

The unit tests use mocks, so one real check is worth doing:

```bash
docker compose up -d && sleep 25
# start fraud-service and payment-service as before, then:
curl -s -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-D5","amount":100,"merchant":"Test","city":"Pune"}'
curl -i http://localhost:8081/api/v1/payments/<ID>                     # expect 200, APPROVED
curl -i http://localhost:8081/api/v1/payments/does-not-exist            # expect 404
curl -i -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-D5","amount":100,"merchant":"Test","city":""}'    # expect 400
```

## E. Commit, push and watch CI

```bash
# commit 1: the code
git add .github fraud-service/src/test payment-service/src/test
git commit -m "test: add service, controller and boundary tests; add CI workflow"

# commit 2: the test document (same pattern as Days 1 to 4)
git add day5-tests-ci-test.md
git commit -m "test: add day5 tests and CI test document"

git push -u origin feature/day5-tests-ci
```

Open a pull request into `main`, then open the repo **Actions** tab.

| Check | Expected | Pass / Fail |
|---|---|---|
| Workflow `CI` starts on the pull request | Yes | |
| `Unit tests (fraud-service)` | Green | |
| `Unit tests (payment-service)` | Green | |
| `Build (fraud-service)` and `Build (payment-service)` | Green | |

Merge only when all are green, using a merge commit (not squash), as for Days 3 and 4. Then:

```bash
git checkout main && git pull
```

After the merge, add the CI badge in a second small branch, `docs/ci-badge` (section F).

## F. Add the CI badge (only after a green run on `main`)

Create the branch `docs/ci-badge` from the updated `main`, add this under the title in `README.md`, commit with `docs: add CI badge`, and open a pull request:

```
[![CI](https://github.com/rohantibile/payment-fraud-detection/actions/workflows/ci.yml/badge.svg)](https://github.com/rohantibile/payment-fraud-detection/actions/workflows/ci.yml)
```

## G. Results

| ID | Check | Expected | Actual | Pass / Fail |
|---|---|---|---|---|
| U-F | fraud-service unit tests | 25 pass | | |
| U-P | payment-service unit tests | 14 pass | | |
| C1-C6 | Controller checks (section C) | all pass | | |
| R | Real app: GET 200, 404, empty city 400 | as listed | | |
| CI | GitHub Actions run | all green | | |

## H. Common problems

| Symptom | Fix |
|---|---|
| `package org.mockito does not exist` or `org.assertj` | The test starter is missing in that service's `pom.xml`. Send the `pom.xml` and I will fix it. |
| `cannot find symbol ... ReflectionTestUtils` | Same cause: `spring-test` is missing from the test classpath. |
| Controller test fails with a JSON or `HttpMediaTypeNotAcceptable` error | The JSON converter was not picked up in standalone mode. Send the error and I will add it explicitly. |
| `permission denied: ./mvnw` | Run `chmod +x mvnw`. |
| Actions run shows `No tests were executed` | Check the test files are under `src/test/java` at the paths in section A. |
| Unexpected BLOCKED in section D | The card was used already. Use a new `cardId`. |

## I. Interview questions (answer in your own words)

1. Why does `FraudProcessingService` publish the verdict before saving the record, and what happens if the save then fails?
2. The consumer crashes after `process` finishes but before `ack.acknowledge()`. What does Kafka do next, and why is that safe?
3. Why are the rules tested without mocks, while the service is tested with them?
4. A dead-letter message needs reprocessing. What would you build, and what could go wrong?
