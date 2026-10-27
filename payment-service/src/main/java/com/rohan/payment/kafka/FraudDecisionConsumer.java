package com.rohan.payment.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.rohan.payment.config.KafkaTopicConfig;
import com.rohan.payment.dto.FraudDecisionEvent;
import com.rohan.payment.service.PaymentService;

@Component 
public class FraudDecisionConsumer {
    private static final Logger log = LoggerFactory.getLogger(FraudDecisionConsumer.class);
    private static PaymentService service;

    public FraudDecisionConsumer(PaymentService service) {
        this.service = service;
    }

    @KafkaListener(topics = KafkaTopicConfig.FRAUD_DECISIONS)
    public void onDecision(@Payload FraudDecisionEvent event){
        log.info("Received decision {} for payment {}", event.decision(), event.paymentId());
        service.applyDecision(event);
    }
}
