## Day 1 Test: Infra + Payment Service CRUD

Verified in GitHub Codespaces. 
- Goal: the Docker stack runs and `POST /payments` saves a `PENDING` payment to MySQL that can be fetched with `GET`.

### 1. Start infrastructure

From the project root:

```bash
docker compose up -d
docker compose ps
```

Expected: 4 containers (`zookeeper`, `kafka`, `kafka-ui`, `mysql`) show `running`. Wait about 20 seconds for MySQL.

Kafka UI: http://localhost:8080

### 2. Verify databases exist

```bash
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e "SHOW DATABASES;"
```

Expected: `paymentdb` and `frauddb` are listed.

### 3. Check Java version

```bash
java -version
```

Expected: 17 or higher.

### 4. Start payment-service

```bash
cd payment-service
chmod +x mvnw
./mvnw spring-boot:run
```

Expected log: `Started PaymentServiceApplication` and `Tomcat started on port 8081`.

Swagger UI: http://localhost:8081/swagger-ui.html

### 5. Create a payment (open a second terminal)

```bash
curl -i -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-1001","amount":2500.50,"merchant":"Amazon","city":"Pune"}'
```

Expected: HTTP `201` with `"status":"PENDING"` and an `id`.

### 6. Fetch the payment

```bash
curl http://localhost:8081/api/v1/payments/<PAYMENT_ID>
```

Expected: the same payment is returned.

### 7. Validation test

```bash
curl -i -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"cardId":"CARD-1001","amount":-5,"merchant":"Amazon","city":"Pune"}'
```

Expected: HTTP `400`.

### 8. Not found test

```bash
curl -i http://localhost:8081/api/v1/payments/does-not-exist
```

Expected: HTTP `404`.

### 9. Verify the database row

```bash
docker exec -it $(docker compose ps -q mysql) mysql -uroot -proot -e "SELECT id, card_id, amount, status FROM paymentdb.payments;"
```

Expected output:

```
+--------------------------------------+-----------+---------+---------+
| id                                   | card_id   | amount  | status  |
+--------------------------------------+-----------+---------+---------+
| 13cf0117-b264-4e36-8260-47a5e87ee352 | CARD-1001 | 2500.50 | PENDING |
+--------------------------------------+-----------+---------+---------+
```

### 10. Clean up

```bash
# Ctrl+C to stop payment-service, then:
docker compose stop     # keeps data
docker compose down     # removes containers
docker compose down -v  # removes containers and database data
```

### Day 1 checklist

- [x] 4 containers running
- [x] Kafka UI opens
- [x] `POST` returns `201 PENDING`
- [x] `GET` returns the same payment
- [x] Row visible in the `payments` table
- [x] Validation returns `400`
- [x] Unknown id returns `404`

### Common problems

| Symptom | Fix |
|---|---|
| `Communications link failure` | MySQL is not ready yet. Wait 20 seconds and retry. |
| `Unknown database 'paymentdb'` | Run `docker compose down -v`, then `docker compose up -d`. |
| `cannot find symbol` | Check that `package` lines say `com.rohan.payment`. |
| `permission denied: ./mvnw` | Run `chmod +x mvnw`. |

### Test Result & Output

<img width="1996" height="626" alt="image" src="https://github.com/user-attachments/assets/da36be4d-df6e-417a-89c7-12c2a188bcde" />
