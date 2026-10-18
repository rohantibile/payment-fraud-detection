package com.rohan.fraud.rules;

import com.rohan.fraud.dto.PaymentEvent;
import com.rohan.fraud.model.Decision;
import com.rohan.fraud.model.TransactionRecord;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Component
public class AmountRule implements FraudRule {

    static final String NAME = "AMOUNT";
    static final BigDecimal LIMIT = new BigDecimal("100000");

    @Override
    public RuleResult evaluate(PaymentEvent event, List<TransactionRecord> history) {
        if (event.amount().compareTo(LIMIT) > 0) {
            return new RuleResult(NAME, Decision.FLAGGED,
                    "Amount " + event.amount() + " exceeds " + LIMIT);
        }
        return RuleResult.pass(NAME);
    }
}