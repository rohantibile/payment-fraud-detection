package com.rohan.fraud.service;

import com.rohan.fraud.dto.FraudDecisionEvent;
import com.rohan.fraud.dto.PaymentEvent;
import com.rohan.fraud.engine.FraudEngine;
import com.rohan.fraud.engine.FraudVerdict;
import com.rohan.fraud.kafka.FraudDecisionProducer;
import com.rohan.fraud.model.TransactionRecord;
import com.rohan.fraud.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class FraudProcessingService {

    // Longest rule window (geo = 10 minutes)
    private static final Duration LOOKBACK = Duration.ofMinutes(10);

    private final TransactionRepository repository;
    private final FraudEngine engine;
    private final FraudDecisionProducer producer;

    public FraudProcessingService(TransactionRepository repository, FraudEngine engine,
                                  FraudDecisionProducer producer) {
        this.repository = repository;
        this.engine = engine;
        this.producer = producer;
    }

    public void process(PaymentEvent event) {
        List<TransactionRecord> history = repository.findByCardIdAndCreatedAtGreaterThanEqual(
                event.cardId(), event.createdAt().minus(LOOKBACK));

        FraudVerdict verdict = engine.evaluate(event, history);

        repository.save(new TransactionRecord(event.paymentId(), event.cardId(), event.amount(),
                event.city(), event.createdAt(), verdict.decision(), verdict.reason()));

        producer.publish(new FraudDecisionEvent(event.paymentId(), event.cardId(),
                verdict.decision(), verdict.reason(), Instant.now()));
    }
}