package com.rohan.payment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
    @NotBlank String cardId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank String merchant,
    @NotBlank String city) { 
}
