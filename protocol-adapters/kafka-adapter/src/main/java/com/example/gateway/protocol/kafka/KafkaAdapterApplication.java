package com.example.gateway.protocol.kafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
// @ComponentScan(basePackages = {"com.example.gateway.protocol.kafka", "com.example.gateway.core.model"})
public class KafkaAdapterApplication {
    public static void main(String[] args) {
        // This application will try to connect to Kafka.
        // Ensure Kafka is running and accessible for standalone testing.
        SpringApplication.run(KafkaAdapterApplication.class, args);
        System.out.println("Kafka Adapter Application Started. Listening for messages...");
    }
}
