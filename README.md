# payment-fraud-detection
Real-time payment fraud detection with Spring Boot and Kafka.
Badges: Build passing (GitHub Actions), Java 17, Spring Boot, Kafka, Docker, and the Open in Codespaces button.

---
### Demo

---
### Problem and solution

---
### Architecture Diagram
<img width="600" height="400" alt="image" src="https://github.com/user-attachments/assets/8d4e3a42-5ab6-4c15-93ad-c57a7a134958" />

---
### Tech Stack with use
- Built an event-driven payment fraud detection system with 2 Spring Boot microservices communicating over Apache Kafka, with a full request, decision and status-update loop.
- Designed a pluggable rule engine (Strategy pattern) with velocity, amount and geo-mismatch checks, so new rules need no changes to existing code.
- Ensured reliable processing with idempotent consumers, manual offset commits, key-based partitioning (per-card ordering) and a dead-letter topic after 3 retries.
- Containerised the full stack (Kafka, MySQL, Kafka UI) with Docker Compose for one-command setup, with JUnit and Mockito tests for every rule.

---
### Key features
- Rule engine using the Strategy pattern, with 3 rules.
- Idempotent consumers.
- Manual offset commit.
- Retry and dead-letter topic.
- Key-based partitioning (per-card ordering).

---
### Fraud rules table
- Rule: Condition	Result
- Velocity: More than 3 payments from one card in 60 seconds	FLAGGED or BLOCKED
- Amount: Over ₹1,00,000	FLAGGED
- Geo mismatch: Different cities within 10 minutes	BLOCKED
