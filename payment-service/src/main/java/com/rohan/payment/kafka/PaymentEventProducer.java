package com.rohan.payment.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.rohan.payment.config.KafkaTopicConfig;
import com.rohan.payment.dto.PaymentEvent;
import org.springframework.kafka.core.KafkaTemplate;

@Component 
public class PaymentEventProducer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PaymentEvent event) {
        // key = cardId, so all events of one card go to the same partition (ordering)
        kafkaTemplate.send(KafkaTopicConfig.PAYMENT_EVENTS, event.cardId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish payment {}", event.paymentId(), ex);
                        } else {
                            log.info("Published payment {} to partition {} offset {}",
                                event.paymentId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                        }
                    });
    }
}
