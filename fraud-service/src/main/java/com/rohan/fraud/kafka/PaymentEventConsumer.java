package com.rohan.fraud.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.rohan.fraud.dto.PaymentEvent;
import com.rohan.fraud.service.FraudProcessingService;

@Component
public class PaymentEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final FraudProcessingService service;

    public PaymentEventConsumer(FraudProcessingService service) {
        this.service = service;
    }

    @KafkaListener(topics = "payment-events")
    public void onPayment(@Payload PaymentEvent event,
                          @Header(KafkaHeaders.RECEIVED_KEY) String key,
                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                          @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received payment {} card={} amount={} city={} (key={}, partition={}, offset={})",
                event.paymentId(), event.cardId(), event.amount(), event.city(),
                key, partition, offset);
        service.process(event);
    }
}