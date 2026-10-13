package com.rohan.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.rohan.payment.entity.Payment;

public record PaymentEvent(
    String paymentId, String cardId, BigDecimal amount,
    String merchant, String city, Instant createdAt) {

        public static PaymentEvent from(Payment p) {
            return new PaymentEvent(p.getId(), p.getCardId(), p.getAmount(), p.getMerchant(), p.getCity(), p.getCreatedAt());
        }
}