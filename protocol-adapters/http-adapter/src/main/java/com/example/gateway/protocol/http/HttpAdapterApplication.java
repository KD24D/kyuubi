package com.example.gateway.protocol.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

// Since GatewayPipelineOrchestrator isn't available yet, this app won't fully function
// but it allows checking if the HTTP endpoint setup is correct.
// We might need to exclude orchestrator autoconfiguration if it were a real bean.
@SpringBootApplication
// Scan components in this module. If core models were in a different base package not scanned by default:
// @ComponentScan(basePackages = {"com.example.gateway.protocol.http", "com.example.gateway.core.model"})
public class HttpAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(HttpAdapterApplication.class, args);
    }
}
