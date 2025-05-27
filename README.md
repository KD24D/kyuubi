# Data Gateway

This project is a modular and configurable Data Gateway designed to handle various data ingestion, transformation, and routing tasks. It supports multiple protocols, data formats, and dynamic processing logic.

## Overview

The Data Gateway focuses on:
*   **Unified Ingress:** Standardizing data intake from diverse sources and protocols.
*   **Format Agnostic Processing:** Normalizing various data formats (JSON, CSV, Protobuf) into a common internal representation.
*   **Flexible Transformations:** Allowing custom data manipulations using Groovy scripting.
*   **Intelligent Flow Control:** Managing data flow with dynamic rate limiting and content-based routing.
*   **Extensibility:** Designed with a modular architecture to easily add new protocols, formats, or processing stages.

## Modules

*   `gateway-app`: Main application, packaging and running the gateway.
*   `gateway-core`: Core data structures, pipeline orchestrator, and egress services.
*   `protocol-adapters`: Handles various ingress protocols.
    *   `http-adapter`: For HTTP/S ingress.
    *   `kafka-adapter`: For Kafka message consumption.
    *   `grpc-adapter`: (Planned/Implemented) For gRPC ingress.
    *   `mqtt-adapter`: (Planned/Implemented) For MQTT message subscription.
*   `format-normalization`: Services for parsing, validating, and converting data formats.
*   `transformation-engine`: Executes data transformations (e.g., using Groovy scripts).
*   `routing-limiting-layer`: Manages rate limiting and dynamic routing of data.
*   `admin-api`: (Planned/Implemented) Provides administrative endpoints.


## Prerequisites

*   Java JDK 17 (or the version specified in `build.gradle`).
*   Gradle (the project uses Gradle wrapper `gradlew`, so a local Gradle installation is not strictly required after initial setup).
*   Running Kafka instance (if using Kafka adapter, for default configurations: `localhost:9092`).
*   (Optional) MQTT Broker if using MQTT adapter.
*   (Optional) Protobuf compiler (`protoc`) if you need to modify `.proto` files and recompile them locally (the build integrates this).

## Building the Project

The project uses Gradle wrapper for building.

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd data-gateway
    ```

2.  **Build the project:**
    *   On Linux or macOS:
        ```bash
        ./gradlew build
        ```
    *   On Windows:
        ```bash
        gradlew.bat build
        ```
    This command will compile all modules, run tests, and create executable JARs/WARs (depending on module configuration). The primary executable JAR will be in `gateway-app/build/libs/`.

    *Note on Protobuf Generation:* The build process includes generating Java classes from `.proto` files located in `format-normalization/src/main/proto/`. If the Gradle wrapper (`gradlew`, `gradle-wrapper.jar`) is not fully set up in your environment, this step might fail. The `gradle-wrapper.properties` is configured, which should allow a compatible Gradle version to be used.

## Running the Gateway

The main application is packaged in the `gateway-app` module.

1.  **Ensure prerequisites are running** (e.g., Kafka).

2.  **Run the application:**
    ```bash
    java -jar gateway-app/build/libs/gateway-app-0.0.1-SNAPSHOT.jar
    ```
    (Adjust the JAR filename based on the actual version in `build.gradle`).

3.  The gateway will start, and you should see log output indicating the services being initialized, HTTP listeners starting, Kafka consumers connecting, etc.

By default, the HTTP adapter (if enabled and part of `gateway-app`) might run on port `8080` (configurable in `application.yaml`).

## Configuration

The main configuration file for the application is located at `gateway-app/src/main/resources/application.yaml`.

Key configuration aspects include:

*   **Server Port:** `server.port` (e.g., for the HTTP ingestion endpoint).
*   **Logging Levels:** Under the `logging.level` section.
*   **Kafka Brokers:** `spring.kafka.producer.bootstrap-servers`, `spring.kafka.consumer.bootstrap-servers`.
*   **Gateway Specific Settings:** Under the `gateway` prefix:
    *   `gateway.routing.default-destination`: Specifies where unrouted messages go.
    *   `gateway.routing.rules-config-path`: Path to an external YAML file for routing rules (e.g., `classpath:routing-rules.yaml`).
    *   `gateway.routing.rules`: Allows embedding routing rules directly in `application.yaml`.
        *   Each rule has `id`, `priority`, `conditionSpel` (a Spring Expression Language string for the condition), and `destination` (type, target, properties). `targetIsSpel` indicates if the destination target is also a SpEL expression.
    *   `gateway.rate-limiting`: (Configuration to be detailed further) Placeholder for rate limit profiles and assignments.
    *   `gateway.transformation-engine.script-location-pattern`: Location of Groovy transformation scripts.
    *   `gateway.transformation-engine.cache-scripts`: Whether to cache compiled Groovy scripts.
    *   Protocol adapter configurations (e.g., topics for Kafka consumers, ports for HTTP/gRPC) will also reside under `gateway.protocol-adapters` or specific keys.

**Example Routing Rule Structure (in `routing-rules.yaml` or embedded under `gateway.routing.rules`):**
```yaml
rules:
  - id: "my_rule_id"
    description: "Description of the rule"
    priority: 100
    conditionSpel: "transformedPayload['field_name'] == 'some_value' && sourceInfo.protocol.name() == 'HTTP'"
    destination:
      type: KAFKA_TOPIC # Or HTTP_ENDPOINT, etc.
      target: "output-topic-name" # Or an HTTP URL, or a SpEL expression for dynamic target
    targetIsSpel: false # Set to true if 'target' is a SpEL expression
```

Refer to `DESIGN.MD` for a more detailed explanation of the intended configuration structure for each component. Configuration properties classes (e.g., `RoutingProperties.java`) map these YAML structures to Java objects.

### Environment Variables
Configuration properties can be overridden using environment variables. For example, `server.port` can be set by `SERVER_PORT=8081`. Spring Boot's relaxed binding rules apply.

## Testing

Unit tests and integration tests are part of the modules.
*   Run all tests: `./gradlew test` (or `gradlew.bat test`).
*   Integration tests in `gateway-app` use the `@SpringBootTest` framework and can be run individually or as part of the build. Test-specific configurations can be placed in `gateway-app/src/test/resources/application-test.yaml`.

## Next Steps / Future Enhancements (Optional Section)

*   Full implementation of gRPC and MQTT adapters.
*   Complete Protobuf support once build environment issues are resolved.
*   Dynamic configuration reloading via Admin API.
*   More sophisticated error handling and dead-lettering strategies.
*   Distributed tracing and comprehensive metrics dashboards.
*   Security enhancements for data in transit and at rest.

---
(Any existing license information or contribution guidelines can be kept at the end)
