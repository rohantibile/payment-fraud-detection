package com.rohan.fraud.engine;

import com.rohan.fraud.dto.PaymentEvent;
import com.rohan.fraud.model.Decision;
import com.rohan.fraud.model.TransactionRecord;
import com.rohan.fraud.rules.FraudRule;
import com.rohan.fraud.rules.RuleResult;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FraudEngine {

    private final List<FraudRule> rules;

    // Spring injects every FraudRule bean, so adding a new rule needs no change here
    public FraudEngine(List<FraudRule> rules) {
        this.rules = rules;
    }

    public FraudVerdict evaluate(PaymentEvent event, List<TransactionRecord> history) {
        List<RuleResult> triggered = rules.stream()
                .map(rule -> rule.evaluate(event, history))
                .filter(result -> result.decision() != Decision.APPROVED)
                .toList();

        if (triggered.isEmpty()) {
            return new FraudVerdict(Decision.APPROVED, "No rules triggered");
        }
        Decision worst = triggered.stream()
                .map(RuleResult::decision)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        String reason = triggered.stream()
                .map(r -> r.rule() + ": " + r.reason())
                .collect(Collectors.joining("; "));
        return new FraudVerdict(worst, reason);
    }
}