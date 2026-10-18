package com.rohan.fraud.rules;

import com.rohan.fraud.model.Decision;

public record RuleResult(String rule, Decision decision, String reason) {
    public static RuleResult pass(String rule) {
        return new RuleResult(rule, Decision.APPROVED, null);
    }
}
