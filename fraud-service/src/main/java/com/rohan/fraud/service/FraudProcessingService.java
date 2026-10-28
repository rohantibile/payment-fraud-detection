package com.rohan.fraud.service;

import com.rohan.fraud.dto.FraudDecisionEvent;
import com.rohan.fraud.dto.PaymentEvent;
import com.rohan.fraud.engine.FraudEngine;
import com.rohan.fraud.engine.FraudVerdict;
import com.rohan.fraud.kafka.FraudDecisionProducer;
import com.rohan.fraud.model.TransactionRecord;
import com.rohan.fraud.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class FraudProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FraudProcessingService.class);
    // Longest rule window (geo = 10 minutes)
    private static final Duration LOOKBACK = Duration.ofMinutes(10);

    private final TransactionRepository repository;
    private final FraudEngine engine;
    private final FraudDecisionProducer producer;

    // Demo hook: when true, a payment with merchant SIMULATE_FAILURE always fails
    @Value("${fraud.simulate-failure:false}")
    private boolean simulateFailure;

    public FraudProcessingService(TransactionRepository repository, FraudEngine engine,
                                  FraudDecisionProducer producer) {
        this.repository = repository;
        this.engine = engine;
        this.producer = producer;
    }

    public void process(PaymentEvent event) {
        // Idempotency: a payment that already has a stored verdict is skipped
        if (repository.existsById(event.paymentId())) {
            log.info("Duplicate payment {} skipped", event.paymentId());
            return;
        }
        if (simulateFailure && "SIMULATE_FAILURE".equals(event.merchant())) {
            throw new IllegalStateException("Simulated failure for payment " + event.paymentId());
        }

        List<TransactionRecord> history = repository.findByCardIdAndCreatedAtGreaterThanEqual(
                event.cardId(), event.createdAt().minus(LOOKBACK));
        FraudVerdict verdict = engine.evaluate(event, history);

        // Publish first: if this fails, nothing is saved, so the retry starts clean.
        // If the save fails afterwards, the retry may republish the same verdict, which
        // payment-service ignores because the payment is no longer PENDING.
        producer.publish(new FraudDecisionEvent(event.paymentId(), event.cardId(),
                verdict.decision(), verdict.reason(), Instant.now()));

        repository.save(new TransactionRecord(event.paymentId(), event.cardId(), event.amount(),
                event.city(), event.createdAt(), verdict.decision(), verdict.reason()));
    }
}