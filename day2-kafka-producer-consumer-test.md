# Day 2 Test: Kafka Producer and Consumer

Verified in GitHub Codespaces. Goal: `POST /payments` saves the payment and publishes an event to `payment-events`; fraud-service consumes and logs it.

## 1. Start infrastructure

From the project root:

```bash
docker compose up -d
sleep 25
docker compose ps
```

Expected: 3 containers (`kafka`, `kafka-ui`, `mysql`) show `Up` for more than 20 seconds. Kafka runs in KRaft mode, so there is no Zookeeper container.

```bash
docker compose logs kafka --tail 10
```

Expected: a log line such as `Kafka Server started`.

Kafka UI: http://localhost:8080 (open from the Ports tab in Codespaces)

## 2. Start fraud-service (terminal 1)

```bash
cd fraud-service
chmod +x mvnw
./mvnw spring-boot:run
```

Expected: `Started FraudServiceApplication` and a log line showing partitions assigned to `fraud-service-group`.

## 3. Start payment-service (terminal 2)

```bash
cd payment-service
chmod +x mvnw
./mvnw spring-boot:run
```

Expected: `Started PaymentServiceApplication` on port 8081.

## 4. Send a payment (terminal 3)

```bash
curl -i -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-1001","amount":2500.50,"merchant":"Amazon","city":"Pune"}'
```

Expected:
- HTTP `201` with `"status":"PENDING"`
- payment-service log: `Published payment <id> to partition X offset Y`
- fraud-service log: `Received payment <id> card=CARD-1001 amount=2500.50 city=Pune (key=CARD-1001, partition=X, offset=Y)`

## 5. Verify in Kafka UI

1. Open http://localhost:8080
2. Go to **Topics -> payment-events**
3. Confirm the topic has **3 partitions**
4. Open the **Messages** tab and confirm your message is listed with key `CARD-1001`

## 6. Ordering test: same card goes to the same partition

```bash
for i in 1 2 3; do
  curl -s -X POST http://localhost:8081/api/v1/payments \
    -H "Content-Type: application/json" \
    -d '{"cardId":"CARD-1001","amount":100,"merchant":"Amazon","city":"Pune"}' > /dev/null
done
```

Expected: all three `Published payment ... partition X` lines show the **same partition number**.

Then send one payment with a different card:

```bash
curl -s -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-2002","amount":100,"merchant":"Flipkart","city":"Mumbai"}'
```

Expected: it may land on a different partition than `CARD-1001`.

## 7. Fault tolerance test

1. Stop fraud-service with `Ctrl+C` in terminal 1.
2. Send a payment:
   ```bash
   curl -i -X POST http://localhost:8081/api/v1/payments \
     -H "Content-Type: application/json" \
     -d '{"cardId":"CARD-3003","amount":500,"merchant":"Swiggy","city":"Delhi"}'
   ```
   Expected: still `201`, and payment-service logs `Published payment ...`.
3. Start fraud-service again:
   ```bash
   cd fraud-service && ./mvnw spring-boot:run
   ```
   Expected: fraud-service logs `Received payment ... card=CARD-3003`, the message sent while it was down. Kafka kept it.

## 8. Clean up

```bash
# Ctrl+C in both app terminals, then:
docker compose stop     # keeps data
docker compose down     # removes containers
docker compose down -v  # removes containers and database data
```

Stop the Codespace from the bottom-left menu to save free hours.

## Day 2 checklist

- [x] Topic `payment-events` works
- [x] Producer logs partition and offset
- [x] Consumer logs the received payment
- [ ] Message visible in Kafka UI with 3 partitions
- [ ] Same card -> same partition
- [ ] Payment sent while fraud-service was down is received after restart

## Fixes applied during Day 2

| Problem | Fix |
|---|---|
| `500` error, `Node -1 disconnected` in logs | Kafka container was crash-looping with `Timed out waiting for connection to Zookeeper`. Replaced Zookeeper-based Kafka with **KRaft mode** (`apache/kafka:3.8.0`) in `docker-compose.yml`. |
| Consumer never received events | `PaymentEventConsumer` was missing `@Component`. Added it. |
| Spring Boot 4 uses Jackson 3 | Changed serializer classes to `JacksonJsonSerializer` and `JacksonJsonDeserializer` in the two `application.yml` files, if the old classes failed. |
| springdoc 2.8.9 targets Boot 3 | Removed the Swagger dependency from payment-service. |

## Common problems

| Symptom | Fix |
|---|---|
| `Node -1 disconnected` in app logs | Kafka is not healthy. Run `docker compose ps` and `docker compose logs kafka --tail 30`. |
| Kafka `Up` time keeps resetting | Crash loop. Check the log for `Killed` or `OutOfMemory` and use a 4-core Codespace machine. |
| `500` after about 60 seconds | Kafka unreachable. Restart the containers, then restart both Spring apps. |
| Consumer logs nothing | Check `@Component` on the consumer and the topic name `payment-events` in both services. |
| Deserialization or `ClassNotFoundException` for the serializer | Check the class names in the `application.yml` files match your Spring Boot version. |
