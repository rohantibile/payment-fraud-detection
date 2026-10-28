package com.rohan.fraud.kafka;

import com.rohan.fraud.config.KafkaTopicConfig;
import com.rohan.fraud.dto.FraudDecisionEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FraudDecisionProducer {

    private static final Logger log = LoggerFactory.getLogger(FraudDecisionProducer.class);
    private final KafkaTemplate<String, FraudDecisionEvent> kafkaTemplate;

    public FraudDecisionProducer(KafkaTemplate<String, FraudDecisionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(FraudDecisionEvent event) {
        try {
            var result = kafkaTemplate
                    .send(KafkaTopicConfig.FRAUD_DECISIONS, event.cardId(), event)
                    .get(10, TimeUnit.SECONDS);
            log.info("Decision {} for payment {} published (partition {}, offset {})",
                    event.decision(), event.paymentId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + event.paymentId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish decision for " + event.paymentId(), e);
        }
    }
}