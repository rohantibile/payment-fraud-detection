package com.rohan.payment.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
    @NotNull String cardId,
    @NotNull @Positive BigDecimal amount,
    @NotNull String merchant,
    @NotNull String city) { 
}
