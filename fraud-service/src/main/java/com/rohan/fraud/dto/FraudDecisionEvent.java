package com.rohan.fraud.dto;

import com.rohan.fraud.model.Decision;
import java.time.Instant;

public record FraudDecisionEvent(
        String paymentId, String cardId, Decision decision, String reason, Instant decidedAt) {
}