package com.rohan.payment.dto;

import java.time.Instant;

public record FraudDecisionEvent(
        String paymentId, String cardId, String decision, String reason, Instant decidedAt) { }