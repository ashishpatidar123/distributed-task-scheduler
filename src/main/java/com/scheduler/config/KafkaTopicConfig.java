package com.scheduler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    
    @Value("${kafka.topics.task-submissions}")
    private String taskSubmissionTopic;

    @Value("${kafka.topics.task-events}")
    private String taskEventsTopic;

    @Value("${kafka.topics.task-dlq}")
    private String taskDlqTopic;

    @Bean
    public NewTopic taskSubmissionTopic() {
        return TopicBuilder.name(taskSubmissionTopic)
                .partitions(4)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic taskEventsTopic() {
        return TopicBuilder.name(taskEventsTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic taskDlqTopic() {
        return TopicBuilder.name(taskDlqTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }

}