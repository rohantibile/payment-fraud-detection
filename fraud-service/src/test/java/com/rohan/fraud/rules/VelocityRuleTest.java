package com.rohan.fraud.rules;

import static com.rohan.fraud.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.rohan.fraud.model.Decision;
import java.util.List;
import org.junit.jupiter.api.Test;

class VelocityRuleTest {

    private final VelocityRule rule = new VelocityRule();

    @Test
    void approvesWhenFewerThanThreeRecentPayments() {
        var history = List.of(past("Pune", 10), past("Pune", 20));
        assertThat(rule.evaluate(event("Pune", "100"), history).decision()).isEqualTo(Decision.APPROVED);
    }

    @Test
    void blocksTheFourthPaymentWithin60Seconds() {
        var history = List.of(past("Pune", 10), past("Pune", 20), past("Pune", 30));
        assertThat(rule.evaluate(event("Pune", "100"), history).decision()).isEqualTo(Decision.BLOCKED);
    }

    @Test
    void ignoresPaymentsOlderThan60Seconds() {
        var history = List.of(past("Pune", 70), past("Pune", 80), past("Pune", 90));
        assertThat(rule.evaluate(event("Pune", "100"), history).decision()).isEqualTo(Decision.APPROVED);
    }
}