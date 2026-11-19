package com.rohan.fraud.rules;

import static com.rohan.fraud.TestData.event;
import static com.rohan.fraud.TestData.past;
import static org.assertj.core.api.Assertions.assertThat;

import com.rohan.fraud.model.Decision;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Edge cases exactly on the rule windows. The rules are pure functions, so no mocks are needed. */
class RuleBoundaryTest {

    @Test
    void velocityCountsPaymentsExactly60SecondsOld() {
        var history = List.of(past("Pune", 60), past("Pune", 60), past("Pune", 60));
        assertThat(new VelocityRule().evaluate(event("Pune", "100"), history).decision())
                .isEqualTo(Decision.BLOCKED);
    }

    @Test
    void velocityIgnoresPaymentsJustOver60SecondsOld() {
        var history = List.of(past("Pune", 61), past("Pune", 61), past("Pune", 61));
        assertThat(new VelocityRule().evaluate(event("Pune", "100"), history).decision())
                .isEqualTo(Decision.APPROVED);
    }

    @Test
    void geoCountsADifferentCityExactlyTenMinutesAgo() {
        var history = List.of(past("Mumbai", 600));
        assertThat(new GeoMismatchRule().evaluate(event("Pune", "100"), history).decision())
                .isEqualTo(Decision.BLOCKED);
    }

    @Test
    void geoIgnoresADifferentCityJustOverTenMinutesAgo() {
        var history = List.of(past("Mumbai", 601));
        assertThat(new GeoMismatchRule().evaluate(event("Pune", "100"), history).decision())
                .isEqualTo(Decision.APPROVED);
    }

    @Test
    void geoApprovesWhenThereIsNoHistory() {
        assertThat(new GeoMismatchRule().evaluate(event("Pune", "100"), List.of()).decision())
                .isEqualTo(Decision.APPROVED);
    }

    @Test
    void amountApprovesASmallPayment() {
        assertThat(new AmountRule().evaluate(event("Pune", "1"), List.of()).decision())
                .isEqualTo(Decision.APPROVED);
    }
}
