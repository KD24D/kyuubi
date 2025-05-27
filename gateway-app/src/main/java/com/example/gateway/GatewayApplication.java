package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan; // To scan @ConfigurationProperties classes

@SpringBootApplication(
    scanBasePackages = { // Explicitly define scan packages to ensure all modules are covered
        "com.example.gateway.core",
        "com.example.gateway.protocol.http", // Assuming HttpIngestionController is here
        "com.example.gateway.protocol.kafka", // Assuming KafkaConsumerService is here
        // Add other protocol adapter base packages if they contain Spring components
        // "com.example.gateway.protocol.grpc",
        // "com.example.gateway.protocol.mqtt",
        "com.example.gateway.formatnormalization",
        "com.example.gateway.transformation",
        "com.example.gateway.routinglimiting",
        "com.example.gateway.config", // For KafkaConfig, etc. in gateway-app
        "com.example.gateway" // For GatewayApplication itself and any other components in this root
    }
)
@ConfigurationPropertiesScan(basePackages = { // Ensure @ConfigurationProperties classes are found
    "com.example.gateway.routinglimiting.routing" // For RoutingProperties
    // Add other packages if they contain @ConfigurationProperties beans
})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
        logger.info("Data Gateway Application Started Successfully!"); // Add logger if desired
    }

    // A simple static logger for the main application class, if needed for startup messages.
    // Alternatively, inject and use a non-static logger.
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GatewayApplication.class);
}
