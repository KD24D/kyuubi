# Data Gateway - Technology Stack Summary

This document outlines the key technologies chosen for the Data Gateway, based on the detailed design in `DESIGN.md`.

**Overall Framework:**
*   **Primary Framework:** Spring Boot (implied by the use of Spring WebFlux, Spring Security, Spring's component model, and configuration management).
*   **Reactive Programming:** Project Reactor (core of Spring WebFlux, used for the processing pipeline).

**1. Protocol Adaptation Layer (Section 4 of DESIGN.md):**
*   **HTTP Ingress:** Spring WebFlux (with Netty as the embedded server).
*   **gRPC Ingress:** `grpc-java` (potentially with `reactor-grpc` for deeper reactive integration).
*   **Kafka Ingress (Consumers):** Apache Kafka Clients (`kafka-clients`).
*   **MQTT Ingress (Subscribers):** Eclipse Paho Java Client or HiveMQ MQTT Client.
*   **Egress (General):** Respective reactive clients where available (e.g., Spring's `WebClient` for HTTP, `Reactor Kafka` for Kafka producers) or standard clients wrapped for asynchronous execution.

**2. Format Normalization and Conversion Layer (Section 5 of DESIGN.md):**
*   **JSON Parsing/Serialization:** Jackson (`com.fasterxml.jackson.core`, `com.fasterxml.jackson.databind`).
*   **JSON Schema Validation:** `com.networknt:json-schema-validator`.
*   **Protobuf Handling:** Protobuf-Java (`com.google.protobuf`).
*   **JSON <-> Protobuf Conversion:** Protobuf `JsonFormat` utility.
*   **CSV Parsing:** Apache Commons CSV (`org.apache.commons:commons-csv`).
*   **CSV to Parquet Conversion:** Apache Parquet (`org.apache.parquet`) and Apache Avro (`org.apache.avro`) (for schema definition and writing via `AvroParquetWriter`).
*   **Parquet to CSV Conversion:** Apache Parquet and Apache Commons CSV.
*   **Generic Data Validation Framework:** Custom implementation using a `Validator` interface, with rules potentially defined in YAML/JSON and loaded via Configuration Management.
*   **Structure Mapping/Pre-transformation:** Apache Commons BeanUtils or custom Java/Groovy logic.

**3. Transformation Engine (Section 6 of DESIGN.md):**
*   **Primary Mechanism:** Groovy scripting (executed via `GroovyShell` or `GroovyScriptEngine`).
*   **Data Selection in Scripts (for JSON-like structures):** JSONPath.
*   **Performance Optimization (Groovy):** Groovy's `@CompileStatic` or `@TypeChecked` annotations.
*   **Extensibility:** Calling custom Java helper classes/utility functions from Groovy.
*   **POJO-to-POJO Mapping (if used in Java helpers for specific cases):** MapStruct or similar patterns.

**4. Intelligent Rate Limiting and Routing Layer (Section 7 of DESIGN.md):**
*   **Rate Limiting:** Bucket4j (for both in-memory per-instance and future distributed rate limiting with JCache/Redis).
*   **Routing Rule Conditions & Dynamic Target Evaluation:** Spring Expression Language (SpEL).
*   **Routing Component:** Custom Spring component managing rule evaluation and dispatch decisions.

**5. Core Processing Pipeline / Orchestrator (Section 8 of DESIGN.md):**
*   **Orchestration Framework:** Reactive Streams via Project Reactor (managed within the Spring Boot application context).
*   **Asynchronous Processing & Backpressure:** Project Reactor Schedulers, `Flux`/`Mono` operators. Potentially internal `java.util.concurrent.BlockingQueue` for specific handoffs if simpler than full reactive chaining for a particular sub-task.
*   **Retry Mechanisms:** Project Reactor retry operators (e.g., `retryWhen`, `retryBackoff`) or Resilience4j `Retry` module.

**6. API and Configuration Management (Section 9 of DESIGN.md):**
*   **Configuration File Format:** YAML.
*   **Configuration Loading & Management:** Spring Boot's native configuration loading mechanism (classpath, external files, environment variables, command-line arguments). Future consideration for Spring Cloud Config or similar.
*   **Configuration Validation:** Bean Validation (JSR 380) via annotations on configuration POJOs, custom `Validator` components.
*   **Administrative API:** RESTful API built with Spring WebFlux (or Spring MVC if parts are synchronous), leveraging Spring Boot Actuator as a base.
*   **Administrative API Security:** Spring Security (for authentication like API Key/Basic Auth and RBAC for authorization).

**7. Monitoring and Logging (Covered in Section 8.5 of DESIGN.md):**
*   **Logging Framework Abstraction:** SLF4J.
*   **Logging Implementation:** Logback (or Log4j2 as an alternative).
*   **Structured Logging:** JSON format via Logback/Log4j2 configuration.
*   **Metrics Collection:** Micrometer.
*   **Metrics Backend (Primary Example):** Prometheus.
*   **Distributed Tracing:** OpenTelemetry (OTel) SDK.
*   **Trace Export Backend (Examples):** Jaeger, Zipkin, or OpenTelemetry Collector.

**8. Build and Dependencies:**
*   **Build Tool:** Maven or Gradle (standard choices for Spring Boot projects, to be finalized at implementation).
*   **Java Version:** Java 17 (or another recent Long-Term Support (LTS) version).

This stack provides a robust, modern, and extensible foundation for the Data Gateway, emphasizing reactive programming for performance and resilience, and strong configurability for adaptability.The `TECHNOLOGY_STACK.md` file has been created successfully.

It summarizes the technology choices for the Data Gateway, organized by major components and capabilities:

1.  **Overall Framework**: Spring Boot and Project Reactor.
2.  **Protocol Adaptation Layer**: Spring WebFlux (HTTP), grpc-java (gRPC), Apache Kafka Clients (Kafka), Eclipse Paho or HiveMQ MQTT Client (MQTT).
3.  **Format Normalization and Conversion Layer**: Jackson (JSON), `com.networknt:json-schema-validator` (JSON Schema), Protobuf-Java (Protobuf), Protobuf `JsonFormat` (JSON <-> Protobuf), Apache Commons CSV (CSV), Apache Parquet & Avro (CSV to Parquet).
4.  **Transformation Engine**: Groovy scripting (with JSONPath for selection), `@CompileStatic` for optimization, and potential for Java helpers (possibly with MapStruct).
5.  **Intelligent Rate Limiting and Routing Layer**: Bucket4j (Rate Limiting), Spring Expression Language (SpEL) (Routing Rules).
6.  **Core Processing Pipeline / Orchestrator**: Project Reactor (Reactive Streams) for orchestration, Reactor Schedulers / `BlockingQueue`s for async processing, Reactor/Resilience4j for retries.
7.  **API and Configuration Management**: YAML (Config Format), Spring Boot configuration (Loading), Spring WebFlux/MVC with Actuator (Admin API), Spring Security (Admin API Security).
8.  **Monitoring and Logging**: SLF4J with Logback/Log4j2 (Logging), Micrometer with Prometheus (Metrics), OpenTelemetry (Tracing).
9.  **Build and Dependencies**: Maven or Gradle, Java 17 (or recent LTS).

This summary accurately reflects the technology decisions detailed throughout the `DESIGN.md` document.
