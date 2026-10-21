package com.rohan.fraud.engine;

import static com.rohan.fraud.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.rohan.fraud.model.Decision;
import com.rohan.fraud.rules.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class FraudEngineTest {

    private final FraudEngine engine = new FraudEngine(
            List.of(new VelocityRule(), new AmountRule(), new GeoMismatchRule()));

    @Test
    void approvesNormalPayment() {
        FraudVerdict v = engine.evaluate(event("Pune", "2500"), List.of());
        assertThat(v.decision()).isEqualTo(Decision.APPROVED);
    }

    @Test
    void flagsHighAmount() {
        FraudVerdict v = engine.evaluate(event("Pune", "150000"), List.of());
        assertThat(v.decision()).isEqualTo(Decision.FLAGGED);
        assertThat(v.reason()).contains("AMOUNT");
    }

    @Test
    void blockWinsOverFlagAndReportsBothReasons() {
        FraudVerdict v = engine.evaluate(event("Mumbai", "150000"), List.of(past("Pune", 120)));
        assertThat(v.decision()).isEqualTo(Decision.BLOCKED);
        assertThat(v.reason()).contains("AMOUNT").contains("GEO_MISMATCH");
    }
}