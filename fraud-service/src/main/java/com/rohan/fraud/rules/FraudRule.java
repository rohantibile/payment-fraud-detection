package com.rohan.fraud.rules;

import java.util.List;
import com.rohan.fraud.dto.PaymentEvent;
import com.rohan.fraud.model.TransactionRecord;

public interface FraudRule {
    RuleResult evaluate(PaymentEvent event, List<TransactionRecord> history);
}
