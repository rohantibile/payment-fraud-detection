package com.rohan.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration 
public class KafkaTopicConfig {
    public static final String PAYMENT_EVENTS = "payment-events";
    public static final String FRAUD_DECISIONS = "fraud-decisions";

    @Bean 
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(PAYMENT_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic fraudDecisionsTopic() {
        return TopicBuilder.name(FRAUD_DECISIONS).partitions(3).replicas(1).build();
    }
}