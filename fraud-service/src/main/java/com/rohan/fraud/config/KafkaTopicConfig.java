package com.rohan.fraud.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String FRAUD_DECISIONS = "fraud-decisions";

    @Bean
    public NewTopic fraudDecisionsTopic() {
        return TopicBuilder.name(FRAUD_DECISIONS).partitions(3).replicas(1).build();
    }
}