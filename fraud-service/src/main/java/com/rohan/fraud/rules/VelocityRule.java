package com.rohan.fraud.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.rohan.fraud.dto.PaymentEvent;
import com.rohan.fraud.model.Decision;
import com.rohan.fraud.model.TransactionRecord;

@Component
public class VelocityRule implements FraudRule{
    static final String NAME = "VELOCITY";
    static final int MAX_PAYMENTS = 3;
    static final Duration WINDOW = Duration.ofSeconds(60);

    @Override
    public RuleResult evaluate(PaymentEvent event, List<TransactionRecord> history){
        Instant from = event.createdAt().minus(WINDOW);
        long recent = history.stream()
                .filter(h -> !h.getCreatedAt().isBefore(from))
                .count();
        if (recent >= MAX_PAYMENTS) {
            return new RuleResult(NAME, Decision.BLOCKED,
                    recent + " payments in the last " + WINDOW.toSeconds() + " seconds");
        }
        return RuleResult.pass(NAME);
    }
    
}
