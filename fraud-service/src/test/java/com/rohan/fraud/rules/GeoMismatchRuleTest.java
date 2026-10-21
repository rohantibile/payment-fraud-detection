package com.rohan.fraud.rules;

import static com.rohan.fraud.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.rohan.fraud.model.Decision;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoMismatchRuleTest {

    private final GeoMismatchRule rule = new GeoMismatchRule();

    @Test
    void approvesSameCity() {
        assertThat(rule.evaluate(event("Pune", "100"), List.of(past("Pune", 60))).decision())
                .isEqualTo(Decision.APPROVED);
    }

    @Test
    void ignoresCityCase() {
        assertThat(rule.evaluate(event("pune", "100"), List.of(past("PUNE", 60))).decision())
                .isEqualTo(Decision.APPROVED);
    }

    @Test
    void blocksDifferentCityWithin10Minutes() {
        assertThat(rule.evaluate(event("Mumbai", "100"), List.of(past("Pune", 300))).decision())
                .isEqualTo(Decision.BLOCKED);
    }

    @Test
    void approvesDifferentCityAfter10Minutes() {
        assertThat(rule.evaluate(event("Mumbai", "100"), List.of(past("Pune", 660))).decision())
                .isEqualTo(Decision.APPROVED);
    }
}