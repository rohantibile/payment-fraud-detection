package com.rohan.fraud.kafka;

import com.rohan.fraud.config.KafkaTopicConfig;
import com.rohan.fraud.dto.FraudDecisionEvent;
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
        kafkaTemplate.send(KafkaTopicConfig.FRAUD_DECISIONS, event.cardId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish decision for {}", event.paymentId(), ex);
                    } else {
                        log.info("Decision {} for payment {} published (partition {}, offset {})",
                                event.decision(), event.paymentId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}