package com.rohan.fraud.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentEvent(
    String paymentId, String cardId, BigDecimal amount,
    String merchant, String city, Instant createdAt){
}