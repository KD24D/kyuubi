package com.example.datagateway.protocol.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for Kafka consumers.
 * Sets up listener container factories, specifically one for consuming raw byte arrays.
 */
@Configuration
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;

    /**
     * Constructs the Kafka consumer configuration.
     * @param kafkaProperties Spring Boot's auto-configured Kafka properties.
     */
    @Autowired
    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    /**
     * Creates a Kafka consumer factory for consumers that process message values as byte arrays.
     * Uses String deserializer for keys and ByteArrayDeserializer for values.
     *
     * @return A {@link ConsumerFactory} configured for {@code <String, byte[]>}.
     */
    @Bean
    public ConsumerFactory<String, byte[]> byteArrayConsumerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null)); // Pass null for SslBundle for now
        // Explicitly set deserializers for this factory, overriding any global defaults if needed
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        // Any other specific overrides for this factory can go here
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Creates a {@link ConcurrentKafkaListenerContainerFactory} for Kafka listeners
     * that expect message values as byte arrays.
     * This factory is named "byteArrayKafkaListenerContainerFactory" and can be referenced
     * by {@code @KafkaListener(containerFactory = "byteArrayKafkaListenerContainerFactory")}.
     * <p>
     * Configures manual acknowledgment mode (MANUAL_IMMEDIATE).
     *
     * @return A configured {@link ConcurrentKafkaListenerContainerFactory}.
     */
    @Bean("byteArrayKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> byteArrayKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(byteArrayConsumerFactory());
        // Configure for manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
