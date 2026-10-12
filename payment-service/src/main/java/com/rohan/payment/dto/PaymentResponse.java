package com.rohan.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.rohan.payment.entity.Payment;
import com.rohan.payment.entity.PaymentStatus;

public record PaymentResponse(
    String id, String cardId, BigDecimal amount, String merchant,
    String city, PaymentStatus status, String statusReason, Instant createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getCardId(), p.getAmount(), p.getMerchant(), p.getCity(), p.getStatus(), p.getStatusReason(), p.getCreatedAt());
    }
}
