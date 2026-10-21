package com.rohan.fraud;

import com.rohan.fraud.dto.PaymentEvent;
import com.rohan.fraud.model.Decision;
import com.rohan.fraud.model.TransactionRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TestData {

    public static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    private TestData() { }

    public static PaymentEvent event(String city, String amount) {
        return new PaymentEvent(UUID.randomUUID().toString(), "CARD-1", new BigDecimal(amount),
                "Amazon", city, NOW);
    }

    public static TransactionRecord past(String city, long secondsAgo) {
        return new TransactionRecord(UUID.randomUUID().toString(), "CARD-1", new BigDecimal("100"),
                city, NOW.minusSeconds(secondsAgo), Decision.APPROVED, null);
    }
}