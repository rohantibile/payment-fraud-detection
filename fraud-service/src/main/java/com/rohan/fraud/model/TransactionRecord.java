package com.rohan.fraud.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "transactions", indexes = @Index(name = "idx_card_created", columnList = "cardId, createdAt"))
public class TransactionRecord {
    @Id 
    private String paymentId;
    @Column(nullable = false) private String cardId;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private String city;
    @Column(nullable = false) private Instant createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Decision decision;
    @Column(nullable = false) private String reason;

    protected TransactionRecord() {}

    public TransactionRecord(String paymentId, String cardId, BigDecimal amount, String city,
                             Instant createdAt, Decision decision, String reason) {
        this.paymentId = paymentId;
        this.cardId = cardId;
        this.amount = amount;
        this.city = city;
        this.createdAt = createdAt;
        this.decision = decision;
        this.reason = reason;
    }

    public String getPaymentId() { return paymentId; }
    public String getCardId() { return cardId; }
    public BigDecimal getAmount() { return amount; }
    public String getCity() { return paymentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Decision getDecision() { return decision; }
    public String getReason() { return reason; }
}
