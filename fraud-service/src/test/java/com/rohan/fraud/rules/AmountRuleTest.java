package com.rohan.fraud.rules;

import static com.rohan.fraud.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.rohan.fraud.model.Decision;
import java.util.List;
import org.junit.jupiter.api.Test;

class AmountRuleTest {

    private final AmountRule rule = new AmountRule();

    @Test
    void approvesExactlyAtTheLimit() {
        assertThat(rule.evaluate(event("Pune", "100000"), List.of()).decision()).isEqualTo(Decision.APPROVED);
    }

    @Test
    void flagsAmountAboveTheLimit() {
        assertThat(rule.evaluate(event("Pune", "100000.01"), List.of()).decision()).isEqualTo(Decision.FLAGGED);
    }
}