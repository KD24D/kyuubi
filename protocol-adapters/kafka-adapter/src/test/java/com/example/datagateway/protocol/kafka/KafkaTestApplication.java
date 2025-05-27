package com.example.datagateway.protocol.kafka;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Minimal Spring Boot application for testing Kafka components.
 * This is used by {@link KafkaIngestionServiceTest}.
 * <p>
 * It needs to scan for components within the kafka-adapter module,
 * especially the configuration properties and configuration classes.
 */
@SpringBootApplication
// Scan for components in the current package and its subpackages (like config)
// Also scan for core components if any were needed for test context (not directly here)
@ComponentScan(basePackages = {"com.example.datagateway.protocol.kafka", "com.example.datagateway.core"})
public class KafkaTestApplication {
    // No main method needed for tests, Spring Boot test context will manage lifecycle.
}
