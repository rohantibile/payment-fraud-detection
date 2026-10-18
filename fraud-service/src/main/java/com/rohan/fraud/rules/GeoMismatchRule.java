package com.rohan.fraud.rules;

import com.rohan.fraud.dto.PaymentEvent;
import com.rohan.fraud.model.Decision;
import com.rohan.fraud.model.TransactionRecord;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class GeoMismatchRule implements FraudRule {

    static final String NAME = "GEO_MISMATCH";
    static final Duration WINDOW = Duration.ofMinutes(10);

    @Override
    public RuleResult evaluate(PaymentEvent event, List<TransactionRecord> history) {
        Instant from = event.createdAt().minus(WINDOW);
        return history.stream()
                .filter(h -> !h.getCreatedAt().isBefore(from))
                .filter(h -> !h.getCity().equalsIgnoreCase(event.city()))
                .findFirst()
                .map(h -> new RuleResult(NAME, Decision.BLOCKED,
                        "Payment from " + event.city() + " within " + WINDOW.toMinutes()
                                + " minutes of a payment from " + h.getCity()))
                .orElseGet(() -> RuleResult.pass(NAME));
    }
}