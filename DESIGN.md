# Data Gateway - High-Level Architecture

## 1. Key Components and Responsibilities

The Data Gateway is designed as a modular system with the following key components:

*   **Protocol Adaptation Layer:**
    *   **Responsibilities:** This layer is the entry and exit point for data. It handles communication with external systems using various protocols.
        *   **Ingress:** Accepts data from source systems via HTTP, gRPC, Kafka messages, or MQTT messages. It translates these protocol-specific requests into a common internal representation.
        *   **Egress:** Sends processed or transformed data to destination systems, potentially using the same set of protocols. (This aspect is more relevant if the gateway also acts as a data dispatcher).

*   **Format Normalization & Validation Layer:**
    *   **Responsibilities:** Ensures that incoming data conforms to expected formats and schemas.
        *   **Parsing:** Parses various data formats like JSON, Protobuf, and CSV into an internal, structured representation.
        *   **Validation:** Validates the parsed data against predefined schemas (e.g., JSON Schema, Protobuf definitions) to ensure data integrity and correctness.
        *   **Normalization (Initial):** May perform initial normalization steps, such as standardizing field names or data types, to prepare data for the transformation engine. For example, it might prepare a CSV for conversion to Parquet by identifying headers and data types.

*   **Transformation Engine:**
    *   **Responsibilities:** Performs data transformations, mapping, and enrichment.
        *   **Mapping:** Transforms data from the source format/schema to the target format/schema. This could involve simple field renaming, complex restructuring, or data type conversions (e.g., CSV to Parquet, JSON to Protobuf).
        *   **Enrichment:** Adds or modifies data by looking up information from external systems or databases.
        *   **Business Logic:** Applies custom business rules or logic to the data.
        *   **Flexibility:** Supports various transformation mechanisms (e.g., using libraries like MapStruct for object mapping, a custom Domain Specific Language (DSL) for defining transformations, or scripting with JSONPath and Groovy).

*   **Intelligent Rate Limiting & Routing Layer:**
    *   **Responsibilities:** Manages the flow of data, prevents system overload, and directs data to appropriate destinations or processing paths.
        *   **Rate Limiting:** Applies rate limits based on various criteria (e.g., client ID, API key, data source) to protect downstream systems and the gateway itself.
        *   **Throttling:** Manages data flow to ensure fair usage and prevent abuse.
        *   **Routing:** Based on data content, metadata, or configuration rules, this layer decides the next step for the data. This could be a specific transformation, a particular egress adapter, or a combination. For example, routing high-priority real-time data differently from batch data.

*   **Core Processing Pipeline/Orchestrator:**
    *   **Responsibilities:** Manages and coordinates the sequence of operations performed on the data as it flows through the gateway.
        *   **Flow Control:** Defines and executes the data processing pipeline, invoking other components (Normalization, Transformation, Routing) in the correct order.
        *   **Error Handling:** Implements strategies for handling errors at different stages of processing (e.g., retry mechanisms, dead-letter queues).
        *   **Transaction Management (if applicable):** Ensures data consistency, especially for operations that require atomicity.
        *   **Asynchronous Processing:** Manages asynchronous tasks and callbacks, crucial for handling non-blocking operations and different data velocities.

*   **Configuration Management Module:**
    *   **Responsibilities:** Provides a centralized way to manage the configuration of all gateway components.
        *   **Storage & Retrieval:** Stores and retrieves configuration for protocol adapters (endpoints, credentials), format schemas, transformation rules, routing logic, rate limits, etc.
        *   **Dynamic Updates:** Allows for dynamic updates to configurations without requiring a full system restart (where feasible).
        *   **Versioning:** May support versioning of configurations to allow rollbacks and track changes.

*   **Monitoring and Logging Module:**
    *   **Responsibilities:** Collects metrics, logs, and traces to provide visibility into the gateway's operation and health.
        *   **Metrics Collection:** Gathers key performance indicators (KPIs) such as request latency, throughput, error rates, resource utilization for each component.
        *   **Logging:** Records detailed information about data processing, errors, and system events for debugging and auditing.
        *   **Tracing:** Implements distributed tracing to track data flow across different components and services.
        *   **Alerting:** Integrates with alerting systems to notify administrators of critical issues.

## 2. Data Flow Description

A typical data flow through the Data Gateway can be visualized as follows:

**Ingress -> Processing -> Egress (Optional)**

1.  **Ingress:**
    *   An external client or system sends data to the **Protocol Adaptation Layer** using a supported protocol (e.g., HTTP POST with JSON payload, a gRPC call, a message to a Kafka topic, or an MQTT publish).
    *   The specific adapter (e.g., HTTP Adapter) receives the data.

2.  **Format Parsing & Validation:**
    *   The raw data is passed to the **Format Normalization & Validation Layer**.
    *   This layer parses the data from its original format (JSON, Protobuf, CSV) into a common internal data structure.
    *   It then validates the data against a predefined schema. If validation fails, an error response might be sent back, or the data might be routed to an error handling flow.

3.  **Transformation (Conditional):**
    *   If transformations are required (based on configuration or routing rules), the normalized data is sent to the **Transformation Engine**.
    *   The engine applies configured mapping rules, data enrichment, or other business logic (e.g., converting CSV data into a structure suitable for Parquet, then performing the conversion).

4.  **Intelligent Rate Limiting & Routing:**
    *   The (optionally transformed) data, along with its metadata, is processed by the **Intelligent Rate Limiting & Routing Layer**.
    *   Rate limits are checked. If a limit is exceeded, the request might be rejected, queued, or throttled.
    *   Routing rules are evaluated to determine the data's next destination or processing path. This could be an internal queue, a specific downstream service, or another transformation step.

5.  **Core Processing & Orchestration:**
    *   The **Core Processing Pipeline/Orchestrator** oversees this entire flow, ensuring components are called in the correct sequence. It handles transitions between states and manages error propagation.
    *   For example, it ensures that after validation, data is passed to transformation if needed, then to routing.

6.  **Egress (if the gateway sends data onwards):**
    *   If the gateway's purpose includes sending data to a final destination (as opposed to just routing to internal services), the routed data is passed back to the **Protocol Adaptation Layer**.
    *   The appropriate egress adapter (e.g., Kafka Producer Adapter, HTTP Client Adapter) formats the data into the target protocol and sends it to the configured downstream system.

**Handling Different Request Types:**

*   **Real-time Requests (e.g., HTTP API call, gRPC request):**
    *   These typically flow through the pipeline synchronously or with low latency asynchronous steps.
    *   The **Protocol Adaptation Layer** might keep the connection open until a response is generated (either success or error).
    *   Rate limiting is critical to ensure responsiveness.
    *   Transformations are usually lightweight.
    *   The **Core Orchestrator** prioritizes quick processing.

*   **Batch Requests (e.g., large CSV file via Kafka, scheduled data ingestion):**
    *   These might be handled more asynchronously.
    *   The **Protocol Adaptation Layer** (e.g., Kafka Consumer) ingests the data.
    *   The **Core Processing Pipeline** might place the data into an internal queue for staged processing to avoid overwhelming resources.
    *   Transformations (e.g., CSV to Parquet) can be more complex and resource-intensive.
    *   The **Transformation Engine** might process data in chunks.
    *   Routing might direct the data to storage systems (e.g., HDFS, S3) or batch processing frameworks.

Throughout the entire flow, the **Monitoring and Logging Module** collects data, and all components retrieve their configurations from the **Configuration Management Module**.

## 3. Architectural Representation (Textual Description)

The Data Gateway can be envisioned as a **layered and modular architecture**.

```
+-----------------------------------------------------------------------------------+
| External Systems (Sources/Sinks)                                                  |
+-----------------------------------------------------------------------------------+
       ^ |                                           ^ |
       | | (Protocols: HTTP, gRPC, Kafka, MQTT)      | | (Protocols)
       v |                                           v |
+-----------------------------------------------------------------------------------+
|                                Protocol Adaptation Layer                          |
|      (HTTP/gRPC/Kafka/MQTT Adapters - Ingress/Egress)                             |
+--------------------------------------|--------------------------------------------+
                                       | (Raw Data / Data to Send)
                                       v
+--------------------------------------|--------------------------------------------+
|                     Format Normalization & Validation Layer                       |
|      (Parsers: JSON, Protobuf, CSV; Validators; Initial Normalizers)              |
+--------------------------------------|--------------------------------------------+
                                       | (Normalized & Validated Data)
                                       v
+--------------------------------------|--------------------------------------------+
|                               Transformation Engine                               |
|      (Mappers, Enrichment Logic, DSL Execution, e.g., CSV to Parquet)             |
+--------------------------------------|--------------------------------------------+
                                       | (Transformed Data)
                                       v
+--------------------------------------|--------------------------------------------+
|                  Intelligent Rate Limiting & Routing Layer                        |
|      (Rate Limiters, Throttlers, Content/Rule-based Routers)                      |
+--------------------------------------|--------------------------------------------+
                                       | (Routed Data / Control Signals)
                                       v
+--------------------------------------|--------------------------------------------+
|                       Core Processing Pipeline/Orchestrator                       |
|      (Manages flow, error handling, async processing coordination)                |
+-----------------------------------------------------------------------------------+
       ^                                      ^                 ^
       | (Config Reads)                       | (Logs/Metrics)  | (Control/Data flow logic)
       v                                      v                 v
+--------------------------+      +--------------------------+
| Configuration Management |      |  Monitoring and Logging  |
| Module                   |      |  Module                  |
+--------------------------+      +--------------------------+
```

**Explanation of Interconnections:**

*   **Layered Flow:** Data generally flows top-down through the layers for ingress processing.
    *   The **Protocol Adaptation Layer** is the outermost layer interacting with external systems.
    *   It passes data to the **Format Normalization & Validation Layer**.
    *   This, in turn, feeds the **Transformation Engine**.
    *   Transformed data then goes to the **Intelligent Rate Limiting & Routing Layer**.
    *   The **Core Processing Pipeline/Orchestrator** underpins and directs these steps, acting as the "brain" that decides which component to call next based on the current state and configuration. It's not strictly a layer data passes *through* in sequence, but rather a central component that interacts with all processing layers.

*   **Central Services:**
    *   The **Configuration Management Module** is a central service accessed by *all* other components to retrieve their operational parameters.
    *   The **Monitoring and Logging Module** is also a central service that *all* other components push data (logs, metrics, traces) to.

*   **Modularity:** Each component is designed to be independent with well-defined responsibilities. This allows for:
    *   **Scalability:** Individual components can be scaled independently (e.g., add more HTTP adapter instances or more transformation workers).
    *   **Maintainability:** Changes within one component are less likely to impact others.
    *   **Flexibility:** New protocols, formats, or transformation logic can be added by implementing new modules or plugins within the respective layers.

*   **Data Representation:** As data moves between layers (e.g., from Normalization to Transformation), it's typically in a common internal, structured representation (e.g., a set of internal objects or a generic data map) to decouple the components.

*   **Feedback Loops:**
    *   The **Routing Layer** can send data back to the **Transformation Engine** for further processing or even to an egress **Protocol Adapter**.
    *   Error conditions detected at any stage can be propagated by the **Orchestrator**, potentially altering the flow (e.g., routing to a dead-letter queue via an egress adapter).

This architecture emphasizes separation of concerns, making the Data Gateway robust, extensible, and manageable.

## 4. Detailed Design: Protocol Adaptation Layer

This section details the design of the Protocol Adaptation Layer, which is responsible for receiving data from various sources using different protocols and converting it into a unified internal representation for further processing.

### 4.1. Common Principles

*   **Configuration:** All adapter-specific configurations (e.g., HTTP ports, Kafka bootstrap servers, MQTT broker URLs, topic names) will be managed by the **Configuration Management Module**.
*   **Monitoring & Logging:** All adapters will integrate with the **Monitoring and Logging Module** to report metrics (e.g., active connections, messages received, errors) and detailed logs.
*   **Asynchronous Processing:** To the extent possible, adapters will operate asynchronously to maximize throughput and responsiveness, especially for I/O-bound operations.

### 4.2. Unified Internal Request Model (Canonical Model)

This model serves as a common data structure for all requests received by the gateway, regardless of the source protocol.

```java
// Illustrative Java-like class definition
public class UnifiedInternalRequest {
    private String requestId; // Unique ID for tracing and logging
    private Object payload; // Raw or initially deserialized payload (e.g., byte[], String, specific DTO if common)
    private PayloadType payloadType; // Enum: JSON, PROTOBUF, XML, CSV, BINARY, TEXT
    private SourceInfo sourceInfo;
    private Map<String, String> metadata; // Key-value pairs for protocol headers, etc.
    private long receivedTimestamp; // Milliseconds since epoch, when the gateway first received it
    private String originalEncoding; // e.g., UTF-8, Base64 for payload

    // Getters and Setters
}

public class SourceInfo {
    private Protocol protocol; // Enum: HTTP, GRPC, KAFKA, MQTT
    private String sourceAddress; // e.g., Client IP, Kafka consumer group, MQTT client ID
    private String requestTarget; // e.g., HTTP URI path, gRPC service/method, Kafka topic, MQTT topic
    // Potentially other source-specific details
}

public enum Protocol { HTTP, GRPC, KAFKA, MQTT }
public enum PayloadType { JSON, PROTOBUF, XML, CSV, BINARY, TEXT, UNKNOWN }
```

**Key Fields:**

*   `requestId`: A unique identifier generated by the adapter (or propagated if available from the source, e.g., Kafka message key or HTTP header). Used for end-to-end tracing.
*   `payload`: The actual data received. It might be kept as raw bytes (`byte[]`) initially, or a `String` for text-based formats. For protocols like gRPC where the message is already deserialized into a generated object, this could be that object.
*   `payloadType`: An enum indicating the presumed type of the payload based on HTTP Content-Type, gRPC definition, or configuration. This helps the next layer (Format Normalization & Validation) to select the correct parser.
*   `sourceInfo`: An object containing details about the source of the request.
    *   `protocol`: The protocol used for communication.
    *   `sourceAddress`: Identifier of the client or source system (e.g., IP address for HTTP, topic for Kafka/MQTT, gRPC client info).
    *   `requestTarget`: Specific target within the protocol (e.g., HTTP path, gRPC method, specific topic).
*   `metadata`: A map to store protocol-specific headers (e.g., HTTP headers, Kafka message headers, MQTT user properties) that might be relevant for routing, transformation, or auditing.
*   `receivedTimestamp`: Timestamp marking when the gateway first received the request.
*   `originalEncoding`: If the payload is textual or was encoded (e.g. Base64), this field stores that information.

### 4.3. HTTP Protocol Adapter

*   **Technology:** Spring WebFlux (for reactive, non-blocking I/O).
*   **Implementation Details:**
    *   **Endpoints Setup:**
        *   Use `@RestController` and `@RequestMapping` annotations to define endpoints.
        *   Endpoints will be configurable, allowing dynamic path mapping based on configuration retrieved from the **Configuration Management Module**. For example, `/ingest/{sourceId}`.
    *   **HTTP Methods:**
        *   **POST:** Primarily used for data ingestion. Request body will contain the payload.
            ```java
            // Example using Spring WebFlux
            @PostMapping("/ingest/{sourceIdentifier}")
            public Mono<ResponseEntity<String>> handlePostRequest(
                @PathVariable String sourceIdentifier,
                @RequestHeader HttpHeaders headers,
                @RequestBody Mono<byte[]> requestBodyMono, // Handle as raw bytes first
                ServerHttpRequest request // For client IP etc.
            ) {
                // ... conversion to UnifiedInternalRequest ...
                // ... forward to Core Processing Pipeline ...
                return Mono.just(ResponseEntity.accepted().body("Request received"));
            }
            ```
        *   **GET:** Can be used for health checks or simple data retrieval if the gateway supports query-like functionalities (less common for pure ingestion).
            ```java
            @GetMapping("/health")
            public Mono<String> healthCheck() {
                return Mono.just("OK");
            }
            ```
    *   **Request Body Handling:**
        *   `@RequestBody Mono<byte[]>`: Preferred way to receive diverse payloads (JSON, Protobuf over HTTP, XML, CSV) without immediate deserialization issues. The `Content-Type` header will be crucial.
        *   The adapter will inspect the `Content-Type` header (e.g., `application/json`, `application/x-protobuf`, `text/csv`, `application/xml`) to set the `payloadType` in the `UnifiedInternalRequest`.
    *   **Header Extraction:**
        *   `@RequestHeader HttpHeaders headers`: Access all HTTP headers.
        *   Relevant headers (e.g., `X-Request-ID`, `Authorization`, custom metadata headers) will be extracted and stored in the `metadata` map of the `UnifiedInternalRequest`.
    *   **Initial Processing Steps:**
        1.  Receive the HTTP request.
        2.  Generate/extract a `requestId`.
        3.  Capture `receivedTimestamp`.
        4.  Extract client IP address from `ServerHttpRequest`.
        5.  Read the request body into `byte[]` (or `String` if `Content-Type` guarantees text).
        6.  Determine `payloadType` from `Content-Type` header. If not present or ambiguous, it might be set to `UNKNOWN` or `BINARY`.
        7.  Populate the `UnifiedInternalRequest` object.
        8.  Pass the `UnifiedInternalRequest` to the **Core Processing Pipeline/Orchestrator**.

### 4.4. gRPC Protocol Adapter

*   **Technology:** `grpc-java` with Reactor gRPC (`reactor-grpc`) for reactive patterns if desired, or standard `grpc-java` for simpler use cases.
*   **Implementation Details:**
    *   **Service Definitions (.proto):**
        *   Define `.proto` files for services the gateway will expose.
        ```protobuf
        syntax = "proto3";

        package com.example.gateway;

        option java_multiple_files = true;
        option java_package = "com.example.gateway.grpc";

        // Generic message for various payloads if specific schema is not enforced at gRPC level
        message IngestRequest {
          string request_id = 1; // Optional: client can provide, or gateway generates
          map<string, string> metadata = 2; // For any gRPC metadata
          PayloadType payload_type = 3; // Enum: JSON_TEXT, PROTOBUF_BYTES, CSV_TEXT etc.
          bytes payload = 4; // Actual payload
        }

        enum PayloadType {
          UNKNOWN = 0;
          JSON_TEXT = 1;
          PROTOBUF_BYTES = 2;
          CSV_TEXT = 3;
          XML_TEXT = 4;
        }

        message IngestResponse {
          string server_request_id = 1;
          string status_message = 2;
        }

        service DataIngestionService {
          rpc IngestData (IngestRequest) returns (IngestResponse);
          // Potentially client-streaming, server-streaming or bi-di streaming RPCs
          // rpc IngestStream (stream IngestRequest) returns (IngestResponse);
        }
        ```
    *   **Server-side Stubs:**
        *   Implement the generated service interface (e.g., `DataIngestionServiceImplBase`).
        *   Override RPC methods.
            ```java
            // Example using grpc-java
            public class DataIngestionServiceImpl extends DataIngestionServiceImplBase {
                @Override
                public void ingestData(IngestRequest grpcRequest, StreamObserver<IngestResponse> responseObserver) {
                    // ... conversion to UnifiedInternalRequest ...
                    // ... forward to Core Processing Pipeline ...

                    IngestResponse response = IngestResponse.newBuilder()
                                                .setServerRequestId(/* retrieved/generated UIR.requestId */)
                                                .setStatusMessage("Request received")
                                                .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                }
            }
            ```
    *   **RPC Handling:**
        *   **Unary:** Standard request/response. The example above is unary.
        *   **Streaming:** For large payloads or continuous data streams, consider client-side or bi-directional streaming. This requires more complex handling in the service implementation to process messages as they arrive.
    *   **Initial Processing Steps:**
        1.  gRPC framework receives and deserializes the `IngestRequest` (or other defined message).
        2.  Extract/generate `requestId` (from `IngestRequest.request_id` or new UUID).
        3.  Capture `receivedTimestamp`.
        4.  Extract client information from gRPC context/interceptors (e.g., peer address).
        5.  The `payload` is already in `grpcRequest.getPayload()` (as `ByteString`). Convert to `byte[]`.
        6.  `payloadType` can be explicitly set in the `IngestRequest.payload_type` by the client, or inferred.
        7.  Populate `metadata` from `grpcRequest.getMetadataMap()`.
        8.  Create `UnifiedInternalRequest`.
        9.  Pass to the **Core Processing Pipeline/Orchestrator**.

### 4.5. Kafka Protocol Adapter

*   **Technology:** Apache Kafka Clients (`kafka-clients`).
*   **Implementation Details:**
    *   **Kafka Consumers:**
        *   Use `KafkaConsumer` to subscribe to topics.
        *   Consumer properties (bootstrap servers, group ID, deserializers, auto-offset-reset) will be configurable.
        *   Run consumers in dedicated threads or a thread pool.
    *   **Topic Subscription:**
        *   Subscribe to a list of topics specified in the configuration. Pattern-based subscription can also be supported.
    *   **Message Deserialization:**
        *   Consumers can be configured with `ByteArrayDeserializer` or `StringDeserializer` initially. The goal is to get the raw payload into the `UnifiedInternalRequest`.
        *   The actual parsing of complex formats (JSON, Protobuf within Kafka message) is deferred to the **Format Normalization & Validation Layer**.
        *   If Kafka messages have a known, fixed schema (e.g., Avro with Schema Registry), this adapter *could* perform initial deserialization to POJOs, but it's generally better to pass raw bytes and `payloadType` for consistency.
    *   **Initial Processing Steps:**
        1.  `KafkaConsumer` polls for records.
        2.  For each `ConsumerRecord`:
            a.  Generate `requestId` (or use Kafka message key if suitable and unique).
            b.  Capture `receivedTimestamp` (can also use `ConsumerRecord.timestamp()`).
            c.  Payload is `ConsumerRecord.value()` (as `byte[]` or `String`).
            d.  `payloadType` might be determined by topic name conventions (e.g., `orders-json-topic` implies JSON) or a message header. If not, defaults to `BINARY` or `UNKNOWN`.
            e.  Kafka message headers (`ConsumerRecord.headers()`) are extracted into `metadata`.
            f.  `sourceInfo` includes topic, partition, offset, consumer group.
            g.  Create `UnifiedInternalRequest`.
            h.  Pass to the **Core Processing Pipeline/Orchestrator**.
        3.  Handle Kafka consumer commits (e.g., manual commit after successful handoff to the pipeline).

### 4.6. MQTT Protocol Adapter

*   **Technology:** MQTT Client (e.g., Eclipse Paho Java Client, HiveMQ MQTT Client).
*   **Implementation Details:**
    *   **MQTT Subscriber:**
        *   Configure the client with broker URL, client ID, credentials.
        *   Implement `MqttCallback` (or equivalent in other libraries) to handle incoming messages.
    *   **Topic Subscription:**
        *   Subscribe to one or more topics/topic filters defined in the configuration.
    *   **Message Handling:**
        *   The `messageArrived(String topic, MqttMessage message)` method will be the entry point.
        *   `MqttMessage.getPayload()` returns `byte[]`.
    *   **QoS Considerations:**
        *   Configure appropriate QoS level for subscriptions (0, 1, or 2) based on reliability requirements. This affects message delivery guarantees between broker and gateway.
        *   The adapter should handle reconnections and resubscriptions if the connection to the broker is lost.
    *   **Initial Processing Steps:**
        1.  MQTT client's callback (`messageArrived`) is invoked.
        2.  Generate `requestId`.
        3.  Capture `receivedTimestamp`.
        4.  Payload is `MqttMessage.getPayload()` (as `byte[]`).
        5.  `payloadType` might be inferred from the topic structure or MQTT user properties (if available and standardized). Otherwise, defaults to `BINARY` or `UNKNOWN`.
        6.  MQTT user properties (if supported by client library and broker) can be extracted into `metadata`.
        7.  `sourceInfo` includes the MQTT topic, client ID.
        8.  Create `UnifiedInternalRequest`.
        9.  Pass to the **Core Processing Pipeline/Orchestrator**.
        10. Acknowledge message based on QoS level (implicit for QoS 0, explicit for QoS 1/2 if library requires manual ack, though often handled by the library after `messageArrived` returns).

### 4.7. Conversion to Canonical Model (Summary)

The primary goal of each adapter is to populate the `UnifiedInternalRequest` object:

| Field               | HTTP Adapter Source                                     | gRPC Adapter Source                                       | Kafka Adapter Source                                      | MQTT Adapter Source                                       |
| ------------------- | ------------------------------------------------------- | --------------------------------------------------------- | --------------------------------------------------------- | --------------------------------------------------------- |
| `requestId`         | Generate UUID / `X-Request-ID` header                   | `IngestRequest.request_id` / Generate UUID                | Kafka message key / Generate UUID                         | Generate UUID                                             |
| `payload`           | HTTP Request Body (`byte[]`)                            | `IngestRequest.payload` (`ByteString` -> `byte[]`)        | `ConsumerRecord.value()` (`byte[]` or `String`)           | `MqttMessage.getPayload()` (`byte[]`)                     |
| `payloadType`       | `Content-Type` header                                   | `IngestRequest.payload_type` / Configuration              | Topic convention / Kafka header / Configuration           | Topic convention / MQTT User Prop / Configuration         |
| `sourceInfo.protocol` | `Protocol.HTTP`                                         | `Protocol.GRPC`                                           | `Protocol.KAFKA`                                          | `Protocol.MQTT`                                           |
| `sourceInfo.sourceAddress` | Client IP Address                                     | Client info from gRPC context                             | Consumer Group ID                                         | MQTT Client ID (gateway's)                                |
| `sourceInfo.requestTarget` | HTTP URI Path                                         | gRPC Service/Method name                                  | Kafka Topic, Partition, Offset                            | MQTT Topic                                                |
| `metadata`          | HTTP Headers                                            | `IngestRequest.metadata` / gRPC metadata                  | Kafka Message Headers                                     | MQTT User Properties (if available)                       |
| `receivedTimestamp` | System time on reception                                | System time on reception                                  | `ConsumerRecord.timestamp()` / System time                | System time on reception                                  |
| `originalEncoding`  | `Content-Encoding` header / `charset` in `Content-Type` | Usually not applicable (binary) or part of `payloadType` | Inferred from `StringDeserializer` or message property    | Inferred if payload is text, or from user property        |

### 4.8. Error Handling in Protocol Adaptation Layer

This layer must handle errors gracefully, providing feedback where possible and ensuring system stability.

*   **Connection Failures:**
    *   **HTTP/gRPC:** Server binding issues (e.g., port already in use) should be logged, and the application might fail to start or report an unhealthy status.
    *   **Kafka/MQTT:**
        *   Inability to connect to brokers: Retry mechanisms with backoff should be implemented. Log connection attempts and failures.
        *   Persistent failures should trigger alerts.
        *   Graceful degradation: If a Kafka/MQTT source is unavailable, the gateway should continue processing from other sources if possible.
*   **Malformed Protocol Envelopes:**
    *   **HTTP:** Invalid HTTP requests (e.g., bad syntax) are typically handled by the underlying HTTP server (e.g., Netty in WebFlux). The adapter might not even see these.
    *   **gRPC:** Malformed gRPC messages are handled by the gRPC framework, which will typically terminate the RPC with an appropriate gRPC status code.
    *   **Kafka/MQTT:** If a message is fundamentally unparseable at the protocol level (rare, as these are usually byte payloads), the respective client library might raise an error. This usually points to a severe issue or misconfiguration. Such messages might need to be skipped and logged.
*   **Deserialization Errors (Initial):**
    *   If an adapter attempts an initial deserialization (e.g., Kafka `StringDeserializer` for non-string data), this will result in an error.
    *   Log the error with details (topic, offset, etc.).
    *   For Kafka, such messages might be sent to a dead-letter topic (DLT) if configured, or the consumer might skip them after logging.
    *   For MQTT, faulty messages are typically just logged and skipped.
*   **Resource Exhaustion:**
    *   Too many open connections (HTTP), high message rates (Kafka/MQTT).
    *   The adapters should have some backpressure mechanisms or rely on the underlying libraries.
    *   Monitoring is key to detect these situations. Rate limiting (in the subsequent layer) is the primary defense.
*   **Error Responses:**
    *   **HTTP:** Send appropriate HTTP status codes (e.g., 400 for bad request if identifiable, 500 for internal server errors, 503 for temporary overload). The response body can contain error details in a standardized format.
    *   **gRPC:** Use gRPC status codes (e.g., `INVALID_ARGUMENT`, `INTERNAL`, `UNAVAILABLE`).
    *   **Kafka/MQTT:** Direct error responses to the producer are not typically possible. Errors are handled by logging, metrics, and potentially routing problematic messages to DLTs.
*   **Configuration Errors:**
    *   Invalid topic names, incorrect credentials, etc.
    *   These should be detected at startup or when configuration is reloaded.
    *   Log errors clearly and potentially prevent the specific adapter from starting or halt the application if critical.

## 5. Detailed Design: Format Normalization and Conversion Layer

This layer is responsible for taking the `UnifiedInternalRequest` from the Protocol Adaptation Layer, parsing its payload based on the `payloadType`, validating it against predefined schemas, and performing initial transformations or conversions to a common internal representation or a target format.

**Input:** `UnifiedInternalRequest` object. The `payload` field (typically `byte[]` or `String`) and `payloadType` (e.g., JSON, PROTOBUF, CSV) are key. Metadata from the `UnifiedInternalRequest` might also inform processing (e.g., a specific schema version passed in a header).

**Output:** A `NormalizedDataEvent` (or similar) object which contains:
*   The original `requestId` and `sourceInfo`.
*   The parsed data in a structured, in-memory representation (e.g., a Java `Map<String, Object>` for JSON, a generated Protobuf object, a list of records for CSV).
*   Metadata about the parsing and validation process (e.g., schema used, validation status).
*   The target `payloadType` if a conversion occurred (e.g., if CSV was converted to Parquet).
*   The potentially transformed payload (e.g., byte array of a Parquet file).

```java
// Illustrative Java-like class definition
public class NormalizedDataEvent {
    private String requestId;
    private SourceInfo sourceInfo; // from UnifiedInternalRequest
    private Object parsedPayload; // e.g., Map<String, Object>, Protobuf Message, List<List<String>> for CSV
    private PayloadType originalPayloadType;
    private PayloadType processedPayloadType; // Could be same as original or new (e.g., PARQUET)
    private byte[] convertedPayload; // Optional: if a format conversion like CSV to Parquet happened
    private ValidationResult validationResult;
    private Map<String, String> processingMetadata; // Schema version used, etc.

    // Getters and Setters
}

public class ValidationResult {
    private boolean isValid;
    private List<String> validationErrors;
    // Getters and Setters
}
```

### 5.1. Format Identification and Dispatching

The `payloadType` field in the `UnifiedInternalRequest` is the primary means to identify the incoming data format. A dispatcher component within this layer will use this information to route the request to the appropriate parser and validator.
*   If `payloadType` is `UNKNOWN`, the layer might attempt to infer the type based on configuration associated with the `sourceInfo.requestTarget` (e.g., a specific Kafka topic always carries JSON).
*   If inference fails, the request is flagged as an error.

### 5.2. Input Format Parsing and Initial Validation

#### 5.2.1. JSON

*   **Technology:** Jackson (`com.fasterxml.jackson.core`, `com.fasterxml.jackson.databind`).
*   **Process:**
    1.  Retrieve the `payload` from `UnifiedInternalRequest`. If it's `byte[]`, convert to `String` using `originalEncoding` (defaulting to UTF-8 if not specified).
    2.  Use `ObjectMapper` from Jackson to parse the JSON string into a generic `Map<String, Object>` or `JsonNode`.
        ```java
        // Example
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> parsedJson = objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
        // or JsonNode rootNode = objectMapper.readTree(jsonString);
        ```
*   **Schema Validation:**
    *   JSON Schemas can be defined and associated with specific sources (e.g., `sourceInfo.requestTarget` or a versioned schema ID from `UnifiedInternalRequest.metadata`).
    *   These schemas are retrieved from the **Configuration Management Module**.
    *   Technology: A library like `com.networknt:json-schema-validator`.
    *   Process:
        1.  Load the JSON Schema definition.
        2.  Convert the parsed JSON (`JsonNode` or `Map`) into a `JsonNode` if not already.
        3.  Validate the `JsonNode` against the schema.
        4.  Validation errors are collected in the `ValidationResult` of the `NormalizedDataEvent`.
        ```java
        // Example (Conceptual)
        // JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        // SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        // JsonSchema schema = schemaFactory.getSchema(schemaJsonNode, config);
        // Set<ValidationMessage> errors = schema.validate(dataJsonNode);
        ```

#### 5.2.2. Protobuf

*   **Technology:** Protobuf-Java (`com.google.protobuf`).
*   **Process:**
    1.  Retrieve the `payload` (which should be `byte[]`) from `UnifiedInternalRequest`.
    2.  Determine the specific Protobuf message type. This requires configuration: mapping a `sourceInfo.requestTarget` or a metadata tag to a specific Protobuf message class (e.g., `com.example.OrderProtos.Order`).
    3.  The corresponding pre-compiled Java class for the Protobuf message must be available in the gateway's classpath. These classes are generated from `.proto` files at build time.
    4.  Use the static `parseFrom(byte[])` method of the generated Protobuf class.
        ```java
        // Example (Assuming Order is a generated Protobuf class)
        // Order orderMessage = Order.parseFrom(protobufBytes);
        // The 'orderMessage' is the parsedPayload.
        ```
*   **Validation:**
    *   Protobuf itself enforces schema adherence during deserialization (field types, presence of required fields as per proto2/proto3 syntax).
    *   If `parseFrom()` fails, it throws an `InvalidProtocolBufferException`, indicating a mismatch between the data and the expected schema. This is a fundamental validation failure.
    *   Further business rule validation can be applied using the Generic Data Validation Framework (see 5.3).

#### 5.2.3. CSV

*   **Technology:** Apache Commons CSV (`org.apache.commons:commons-csv`).
*   **Process:**
    1.  Retrieve the `payload` from `UnifiedInternalRequest`. If `byte[]`, convert to `String` (using `originalEncoding`).
    2.  Configuration for CSV parsing is crucial and retrieved from the **Configuration Management Module** based on `sourceInfo` or metadata. This includes:
        *   Delimiter (e.g., ',', ';', '\t').
        *   Presence of a header row (boolean). If present, headers can be used for mapping.
        *   Expected number of columns (optional, for basic structural validation).
        *   Quote character, escape character, null string representation.
    3.  Use `CSVParser` and `CSVFormat` from Apache Commons CSV.
        ```java
        // Example
        // Reader in = new StringReader(csvString);
        // CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
        //     .setHeader(config.hasHeader()) // boolean from config
        //     .setDelimiter(config.getDelimiter()) // char from config
        //     .setSkipHeaderRecord(config.hasHeader())
        //     .build();
        // CSVParser parser = new CSVParser(in, csvFormat);
        // List<CSVRecord> records = parser.getRecords();
        // The 'records' (or a list of lists/maps derived from it) is the parsedPayload.
        ```
*   **Initial Validation:**
    *   Structural checks:
        *   Does each record have the expected number of columns (if configured)?
        *   Are required headers present (if headers are expected and defined)?
    *   Type checks can be part of the Generic Data Validation Framework or initial transformations.

### 5.3. Generic Data Validation Framework

After initial format-specific parsing, a generic validation mechanism can apply further checks.
*   **Design:**
    *   A `Validator` interface with a `validate(Object parsedPayload, Map<String, String> context)` method.
    *   Implementations of this interface for different types of validation (e.g., `FieldConstraintValidator`, `BusinessRuleValidator`).
    *   Validation rules are defined in a declarative format (e.g., YAML or JSON) and stored in the **Configuration Management Module**. These rules are associated with `sourceInfo` or specific data types.
    *   Example rule definition:
        ```yaml
        validations:
          - sourceTarget: "kafka://orders-topic"
            rules:
              - type: "fieldConstraint"
                field: "orderAmount"
                constraint: "min:0, max:10000"
              - type: "regexMatch"
                field: "email"
                pattern: "^.+@.+\\..+$"
              - type: "customLogic" # Points to a scripted or coded validator
                scriptId: "orderBusinessRule_001"
        ```
*   **Application:**
    1.  The framework loads applicable rules based on the `UnifiedInternalRequest`'s context.
    2.  It iterates through the rules, invoking the corresponding `Validator` implementations.
    3.  Validation failures are aggregated in the `ValidationResult`.
    *   This allows for checks like: field length, numeric ranges, regex patterns, presence of required fields not covered by schema, cross-field validation, or even invoking small scripts (e.g., Groovy) for complex custom logic.

### 5.4. Core Transformation Capabilities

This layer can perform common, often structural, transformations. More complex business logic transformations are typically deferred to the main "Transformation Engine".

#### 5.4.1. JSON <-> Protobuf Bi-directional Conversion

*   **JSON to Protobuf:**
    *   **Process:**
        1.  Input: JSON data (as `Map<String, Object>` or `JsonNode`) and a target Protobuf message type (configured, e.g., `com.example.TargetOrderProtos.TargetOrder`).
        2.  The target `.proto` definition must be available, and its Java class compiled.
        3.  Use Protobuf's `JsonFormat.parser().merge(jsonString, builder)` utility. The JSON string can be generated from the `Map` or `JsonNode`.
            ```java
            // Example
            // TargetOrder.Builder builder = TargetOrder.newBuilder();
            // String jsonToConvert = objectMapper.writeValueAsString(parsedJsonMap);
            // JsonFormat.parser().ignoringUnknownFields().merge(jsonToConvert, builder);
            // TargetOrder protoMessage = builder.build();
            // The 'protoMessage' is the transformed payload.
            ```
    *   **Schema Mismatches:**
        *   `ignoringUnknownFields()` can be used to drop JSON fields not present in the Protobuf schema.
        *   Missing required Protobuf fields in JSON will cause an error during `build()`.
        *   Type mismatches (e.g., JSON string for a Protobuf int field) will cause parsing errors. These need careful handling and clear error reporting.
        *   Nested structures require corresponding nested Protobuf messages.
*   **Protobuf to JSON:**
    *   **Process:**
        1.  Input: A Protobuf message object.
        2.  Use Protobuf's `JsonFormat.printer()` utility.
            ```java
            // Example
            // String jsonOutput = JsonFormat.printer().preservingProtoFieldNames().includingDefaultValueFields().print(protoMessage);
            // The 'jsonOutput' (as String or parsed back to Map/JsonNode) is the transformed payload.
            ```
    *   **Considerations:**
        *   `preservingProtoFieldNames()`: Keeps snake_case names if used in `.proto`. Otherwise, it converts to camelCase.
        *   `includingDefaultValueFields()`: By default, fields with default values (e.g., 0 for int, empty string) are omitted in JSON. This option includes them.

#### 5.4.2. CSV to Parquet Conversion

*   **Process:**
    1.  Input: Parsed CSV data (e.g., `List<CSVRecord>`).
    2.  **Schema Handling:** A schema is essential for Parquet. Options:
        *   **Inferred from CSV Headers:** If CSV has headers, these can form the basis of the schema. Data types might be inferred from the first few rows (e.g., all numeric -> int/long, else string) – this is fragile.
        *   **Explicitly Configured Schema:** Define the schema (field names and types) in the **Configuration Management Module**, associated with the data source. This is the most robust approach.
        *   **Avro Schema as Intermediary:** Define an Avro schema (`.avsc` file or programmatic). This Avro schema is then used to write Parquet files, as Parquet libraries often have good integration with Avro (e.g., `ParquetWriter` via `AvroParquetWriter`).
            ```json
            // Example Avro Schema for a CSV
            // {
            //   "type": "record",
            //   "name": "CsvRecord",
            //   "fields": [
            //     {"name": "column1", "type": "string"},
            //     {"name": "column2", "type": "int"},
            //     ...
            //   ]
            // }
            ```
    3.  **Technology:** Apache Parquet (`org.apache.parquet`), Apache Avro (`org.apache.avro`).
    4.  **Writing Parquet:**
        *   Use `AvroParquetWriter` or a similar Parquet writer that accepts a schema.
        *   Iterate through CSV records. For each `CSVRecord`, create an Avro `GenericRecord` conforming to the Avro schema.
        *   Write the `GenericRecord` to the Parquet writer.
        *   The output is a `byte[]` representing the Parquet file content (usually written to an in-memory byte array stream for this layer).
        ```java
        // Example (Conceptual using Avro intermediary)
        // Schema avroSchema = new Schema.Parser().parse(schemaStringFromConfig);
        // Path outputPath = // create a temporary file path or use ByteArrayOutputStream
        // ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(outputPath)
        //                                     .withSchema(avroSchema)
        //                                     .withCompressionCodec(CompressionCodecName.SNAPPY) // Configurable
        //                                     .build();
        // for (CSVRecord csvRecord : csvRecords) {
        //    GenericRecord avroRecord = new GenericData.Record(avroSchema);
        //    // Populate avroRecord from csvRecord based on schema
        //    writer.write(avroRecord);
        // }
        // writer.close();
        // byte[] parquetBytes = Files.readAllBytes(outputPath); // or from ByteArrayOutputStream
        // This 'parquetBytes' becomes 'convertedPayload', and 'processedPayloadType' becomes PARQUET.
        ```

#### 5.4.3. Parquet to CSV Conversion (Considerations)

*   **Process:**
    1.  Input: Parquet data (`byte[]`).
    2.  Technology: Apache Parquet, Apache Commons CSV.
    3.  Read Parquet records using `ParquetReader` (e.g., `AvroParquetReader` if Avro was used for writing).
    4.  For each record read from Parquet (e.g., an Avro `GenericRecord`), format its fields into a CSV string.
    5.  Assemble these CSV strings, potentially adding a header row derived from the Parquet/Avro schema.
*   This is generally straightforward but requires careful handling of data types and formatting for CSV output.

### 5.5. Structure Mapping & Pre-transformation

Before passing data to the main Transformation Engine, this layer can perform initial, often simpler, structural adjustments. These are typically rule-based and configured per source or data type.
*   **Field Renaming:** E.g., map `customer_id` from input JSON to `customerId` in the internal representation.
*   **Simple Data Type Coercions:**
    *   E.g., convert a string "true" to boolean `true`.
    *   Convert a numeric string "123" to an integer `123`.
    *   These coercions must be clearly defined and handle potential errors (e.g., "abc" cannot be coerced to an integer).
*   **Default Value Population:** Populate missing optional fields with configured default values.
*   **Flattening/Nesting:** Minor restructuring, like flattening a simple nested object or creating a small nested object from flat fields.
*   **Implementation:**
    *   These rules can be defined within the same configuration system as the Generic Data Validation rules.
    *   Applied to the parsed data (e.g., `Map<String, Object>` for JSON) before it's passed to the next stage or before a major format conversion like CSV to Parquet.
    *   Libraries like Apache Commons BeanUtils or simple custom logic can achieve this.

### 5.6. Error Handling

*   **Parsing Errors:**
    *   **Malformed Data:** Invalid JSON, Protobuf that doesn't match the compiled class, malformed CSV.
    *   Log detailed errors including `requestId`, `sourceInfo`, and specific parsing error messages.
    *   The `NormalizedDataEvent` will indicate failure, and the `validationResult` will contain error details.
    *   The event might be routed to an error/dead-letter queue by the **Core Processing Pipeline/Orchestrator**.
*   **Schema Validation Errors (JSON):**
    *   Logged with details.
    *   `ValidationResult` in `NormalizedDataEvent` is populated.
    *   Processing might stop, or data might be flagged and continue, based on configuration (e.g., "fail fast" vs. "validate and flag").
*   **Generic Validation Errors:**
    *   Similar to schema validation errors: logged, `ValidationResult` populated.
*   **Transformation/Conversion Errors:**
    *   **JSON <-> Protobuf:** Schema mismatches, type conversion issues during `JsonFormat` parsing/printing.
    *   **CSV to Parquet:** Schema definition problems, errors converting CSV string values to Avro/Parquet schema types (e.g., "text" in a CSV field defined as `int` in Avro schema).
    *   Logged with details. The `NormalizedDataEvent` would reflect the failure.
*   **Configuration Errors:**
    *   Missing schema definitions, invalid transformation rules.
    *   These should ideally be caught at startup or configuration reload time. Logged critically.
*   **Reporting:**
    *   All errors are logged via the **Monitoring and Logging Module**.
    *   Metrics are updated (e.g., parsing failure count, validation failure count per source).
    *   For synchronous interactions (e.g., HTTP), an appropriate error response should be propagated back by the **Core Processing Pipeline** if a failure occurs in this layer.

## 6. Detailed Design: Transformation Engine

The Transformation Engine is responsible for applying complex data manipulations, mapping, and enrichment logic to the `NormalizedDataEvent` received from the Format Normalization and Conversion Layer.

**Input:** `NormalizedDataEvent` object. This object contains the `parsedPayload` (e.g., `Map<String, Object>` for JSON, Protobuf message object, `List<CSVRecord>`), `originalPayloadType`, and other metadata.

**Output:** A `TransformedDataEvent` object, which includes:
*   The original `requestId` and `sourceInfo`.
*   The transformed payload, which could be in various formats (e.g., a new `Map<String, Object>`, a different Protobuf message, or raw `byte[]` if the transformation results in a specific binary format like a custom binary structure or a file to be sent).
*   The `payloadType` of the transformed payload.
*   Status of the transformation (success, failure).
*   Error details if the transformation failed.

```java
// Illustrative Java-like class definition
public class TransformedDataEvent {
    private String requestId;
    private SourceInfo sourceInfo;
    private Object transformedPayload; // e.g., Map<String, Object>, new Protobuf Message, byte[]
    private PayloadType payloadType; // Type of the transformedPayload
    private TransformationStatus status;
    private List<String> transformationErrors;
    private Map<String, String> processingMetadata; // e.g., transformation rules version applied

    // Getters and Setters
}

public enum TransformationStatus { SUCCESS, FAILURE }
```

### 6.1. Evaluation and Selection of Transformation Technology

#### 6.1.1. MapStruct

*   **Pros:**
    *   Excellent for POJO-to-POJO mapping.
    *   Compile-time code generation, resulting in high performance (no reflection at runtime).
    *   Type-safe.
    *   Good integration with Java development workflows.
*   **Cons:**
    *   Requires predefined Java classes (POJOs) for source and target. Less flexible for dynamic schemas or when input/output structures are not strictly POJOs (e.g., generic `Map<String, Object>`).
    *   Transformations are defined in Java interfaces, requiring recompilation for changes.
    *   Less suitable for complex conditional logic or transformations that require external data lookups directly within the mapping definition.

#### 6.1.2. Custom Expression DSL (Domain Specific Language)

*   **Pros:**
    *   Can be tailored precisely to the transformation needs of the gateway.
    *   Can be designed to be human-readable and easy for non-programmers (or those with specific domain knowledge) to define rules.
    *   Offers full control over syntax, features, and execution.
*   **Cons:**
    *   Significant development effort to design, implement, parse, and maintain the DSL and its execution engine.
    *   Steeper learning curve for users if the DSL is complex.
    *   Performance depends heavily on the DSL's design and interpreter/compiler implementation.

#### 6.1.3. JSONPath + Groovy (or JSLT, JMESPath)

*   **JSONPath:** Excellent for selecting parts of a JSON-like structure (e.g., `Map<String, Object>`).
*   **Groovy:** A powerful, JVM-based scripting language with concise syntax, good Java integration, and metaprogramming capabilities.
*   **JSLT/JMESPath:** Other JSON-to-JSON transformation languages with specific strengths (JSLT is declarative, JMESPath is query-based).

*   **Pros (JSONPath + Groovy):**
    *   **Flexibility:** Groovy can handle complex logic, conditional transformations, external API calls for enrichment, and manipulation of diverse data structures (Maps, Lists, POJOs).
    *   **Dynamic:** Scripts can be loaded and potentially updated at runtime.
    *   **Powerful Selection:** JSONPath makes it easy to extract data from input structures.
    *   **Ease of Use (for developers):** Groovy is relatively easy for Java developers to pick up.
    *   **Rich Ecosystem:** Access to Java libraries within Groovy scripts.
*   **Cons (JSONPath + Groovy):**
    *   **Performance:** Script interpretation/compilation can have overhead compared to compiled Java code. Groovy has its own runtime, adding some overhead.
    *   **Security:** Executing arbitrary scripts requires sandboxing or careful validation if scripts are user-provided.
    *   **Complexity for Non-Developers:** Writing Groovy scripts might be too complex for business users.
    *   **Debugging:** Debugging scripts can be more challenging than debugging compiled Java code.

#### 6.1.4. Recommendation and Justification

A **hybrid approach** is recommended, prioritizing **JSONPath + Groovy scripting** as the primary mechanism, with potential for integrating specific, highly performant Java-based transformers (possibly using MapStruct-like patterns internally for well-defined POJO parts) for common, performance-critical tasks.

**Justification:**

*   **Flexibility and Power:** The gateway needs to handle potentially diverse and evolving data structures and transformation logic. Groovy provides the necessary power for complex manipulations, conditional logic, and data enrichment. JSONPath simplifies data extraction from the common `Map<String, Object>` representation resulting from JSON parsing or after converting other formats.
*   **Handling Diverse Structures:** The `NormalizedDataEvent` often carries data as `Map<String, Object>` or generic Protobuf messages. Scripting languages like Groovy are well-suited for working with these dynamic structures.
*   **Ease of Defining Complex Rules:** While more complex than a simple DSL, Groovy allows for sophisticated rule definition that can cover a wide range of transformation scenarios.
*   **Extensibility:** New functions and integrations can be naturally added via Groovy scripts or by calling out to Java helper classes.
*   **Performance Mitigation:** Groovy scripts can be compiled by the Groovy runtime for better performance after initial loading. For extremely performance-sensitive, common transformations between fixed structures, dedicated Java functions can be invoked from Groovy, or a separate path could use a MapStruct-like approach if POJOs are involved.

This approach offers the best balance for the gateway's requirements. A purely MapStruct approach is too rigid for dynamic schemas, and a fully custom DSL is a large undertaking.

### 6.2. Transformation Rule Definition and Management

#### 6.2.1. Rule Definition

Transformation rules will primarily be defined as **Groovy scripts**. These scripts will operate on the `parsedPayload` from the `NormalizedDataEvent`.

*   **Syntax:** Standard Groovy syntax.
*   **Input to Script:** The script will receive predefined bindings:
    *   `inputPayload`: The `parsedPayload` (e.g., `Map<String, Object>`, `com.google.protobuf.Message`, `List<org.apache.commons.csv.CSVRecord>`).
    *   `sourceInfo`: The `SourceInfo` object.
    *   `metadata`: The `metadata` map from `UnifiedInternalRequest`.
    *   `logger`: A logger instance for script-specific logging.
    *   `context`: A map for passing additional context or utility objects.
*   **Output from Script:** The script is expected to return an object representing the transformed payload. This could be a `Map<String, Object>`, a new Protobuf message, a `String`, or even `byte[]`. The script should also be able to indicate the `PayloadType` of the returned object if it differs from the input.

**Illustrative Examples:**

1.  **Simple Field Mapping and Modification (Input: JSON as Map)**

    ```groovy
    // Rule ID: map_order_v1
    // Description: Maps and transforms fields for an order.

    // Assuming inputPayload is a Map
    def output = [:]
    output.order_id = inputPayload.id
    output.customer_name = inputPayload.customer.firstName + " " + inputPayload.customer.lastName
    output.total_amount = inputPayload.items.sum { it.price * it.quantity }
    output.status = "PROCESSED"
    output.received_at_gateway_timestamp = sourceInfo.receivedTimestamp // Accessing sourceInfo

    // Optionally, specify the output type if it's always JSON
    // context.put("outputPayloadType", "JSON") // Or a dedicated return structure

    return output // This map will be treated as JSON by default if not specified otherwise
    ```

2.  **Conditional Logic (Input: JSON as Map)**

    ```groovy
    // Rule ID: conditional_transform_v1
    def output = inputPayload.clone() // Start with a copy

    if (inputPayload.amount > 1000) {
        output.priority = "HIGH"
    } else {
        output.priority = "NORMAL"
    }

    if (inputPayload.type == "REFUND") {
        output.amount = -Math.abs(inputPayload.amount) // Ensure negative for refunds
    }
    return output
    ```

3.  **Protobuf to Protobuf Transformation (Conceptual)**
    (Requires pre-compiled Protobuf classes on classpath and helper methods/imports)

    ```groovy
    // Rule ID: proto_transform_order_v1
    // import com.example.target.TargetOrderProtos.TargetOrder
    // import com.example.source.SourceOrderProtos.SourceOrder

    // Assuming inputPayload is an instance of SourceOrder
    // SourceOrder sourceOrder = (SourceOrder) inputPayload

    // TargetOrder.Builder builder = TargetOrder.newBuilder()
    // builder.setId(sourceOrder.getOrderId())
    // builder.setCustomerName(sourceOrder.getCustomerDetails().getName())
    // builder.setTotalValue(sourceOrder.getItemsList().sum { it.getPrice() * it.getQuantity() })
    // builder.setStatus("PROCESSED_PROTO")

    // return builder.build() // Return the new Protobuf message
    ```
    *Note: For heavy Protobuf transformations, a Java helper function called from Groovy might be more efficient.*

#### 6.2.2. Rule Storage, Loading, and Management

*   **Storage:**
    *   Transformation rules (Groovy scripts) will be stored as text files (e.g., with a `.groovy` extension).
    *   These files will be organized in a designated directory structure within the gateway's configuration path (e.g., `config/transformations/source_type_A/rule1.groovy`).
    *   This approach integrates well with the **Configuration Management Module**, which can manage these files.
*   **Loading:**
    *   The Transformation Engine will scan the rule directory at startup.
    *   A `GroovyShell` or `GroovyScriptEngine` will be used to parse and compile these scripts. Compiled scripts can be cached for performance.
    *   A mapping will be maintained between a rule identifier (e.g., derived from filename/path or a specific ID within the script) and the compiled `groovy.lang.Script` object.
*   **Rule Identification for Application:**
    *   The selection of which rule(s) to apply will be based on configuration associated with the `NormalizedDataEvent`'s `sourceInfo.requestTarget` (e.g., Kafka topic, HTTP endpoint path) or other metadata.
    *   This configuration (e.g., in a YAML/JSON file) will map source identifiers to one or more Transformation Rule IDs.
    ```yaml
    # Example: transformation_bindings.yaml
    transformationBindings:
      - sourceTarget: "kafka://orders-topic"
        payloadType: "JSON" # Optional: apply only if payload is JSON
        transformationRuleIds:
          - "map_order_v1"
          - "conditional_transform_v1" # Rules can be chained
      - sourceTarget: "/ingest/legacy-data"
        transformationRuleIds:
          - "legacy_data_cleanup_v1"
    ```
*   **Versioning:**
    *   Rule versioning can be handled by naming conventions in filenames (e.g., `map_order_v2.groovy`).
    *   The binding configuration would then specify which version of a rule to use.
    *   Alternatively, versioning can be managed by the underlying version control system used for configuration files.
*   **Dynamic Updates:**
    *   The `GroovyScriptEngine` supports dynamic reloading of scripts if the source file changes. This can be enabled for dynamic rule updates without restarting the gateway.
    *   The Configuration Management Module would need to monitor rule files for changes and trigger a reload in the Transformation Engine. This adds complexity but offers high flexibility.

### 6.3. Engine Architecture and Processing Logic

*   **Core Components:**
    1.  **TransformationRuleRegistry:**
        *   Holds all loaded and compiled Groovy scripts (or references to other transformation logic).
        *   Provides methods to retrieve a compiled script by its ID.
    2.  **TransformationRuleSelector:**
        *   Takes a `NormalizedDataEvent` as input.
        *   Consults the `transformation_bindings.yaml` (or similar configuration) to determine the list of applicable `transformationRuleIds` based on `sourceInfo`, `payloadType`, or metadata.
    3.  **GroovyScriptExecutor:**
        *   Takes a compiled `groovy.lang.Script`, input payload, and context.
        *   Executes the script using `GroovyShell.evaluate()` or `Script.run()`.
        *   Handles setting up bindings (`inputPayload`, `sourceInfo`, `metadata`, `logger`, `context`).
    4.  **TransformationOrchestrator:**
        *   The main component that receives a `NormalizedDataEvent`.
        *   Uses `TransformationRuleSelector` to get the rules.
        *   Iterates through the selected rules. For each rule:
            *   Retrieves the compiled script from `TransformationRuleRegistry`.
            *   Invokes `GroovyScriptExecutor` to run the script. The output of one script becomes the input for the next if chaining is supported and configured.
        *   Constructs the `TransformedDataEvent` with the final result or error information.

*   **Processing Logic:**
    1.  The Transformation Engine receives a `NormalizedDataEvent`.
    2.  The `TransformationOrchestrator` passes the event to the `TransformationRuleSelector`.
    3.  The `TransformationRuleSelector` identifies the sequence of transformation rule IDs to apply from the configuration.
    4.  For each rule ID in sequence:
        a.  The `TransformationOrchestrator` retrieves the compiled script from the `TransformationRuleRegistry`.
        b.  The `GroovyScriptExecutor` executes the script with the current payload (which could be the output of a previous transformation) and context.
        c.  If the script fails, the process is halted, and an error is recorded.
        d.  The result of the script becomes the input for the next script in the chain.
    5.  After all rules in the chain are successfully executed, the final payload is used to create the `TransformedDataEvent` with `status = SUCCESS`.
    6.  If any rule fails, a `TransformedDataEvent` is created with `status = FAILURE` and error details.

*   **Support for Conditional Logic:**
    *   Conditional logic is primarily handled **within** the Groovy scripts themselves using standard Groovy `if/else` statements, `switch`, etc., operating on the `inputPayload` or `metadata`.
    *   The `transformation_bindings.yaml` could potentially support conditional application of entire rule *sets* based on metadata, but this adds more complexity to the selector. For now, script-internal conditionals are preferred for flexibility.

### 6.4. Extensibility

*   **Custom Groovy Functions:**
    *   Define utility functions or classes in separate `.groovy` files within a designated `lib` or `utils` subdirectory of the transformations scripts folder.
    *   These utility scripts can be loaded by the main transformation scripts using Groovy's script compilation and classloading mechanisms (e.g., `evaluate(new File("utils/myUtils.groovy"))` or by pre-compiling them and adding to the `GroovyShell`'s classpath).
    *   This allows for reusable transformation logic.
*   **Java Helper Classes:**
    *   Develop Java classes providing complex or performance-sensitive utility functions.
    *   These classes can be included in the gateway's classpath.
    *   Groovy scripts can then instantiate and call methods on these Java classes directly.
        ```groovy
        // Example: Calling a Java helper
        // def javaHelper = new com.example.gateway.utils.TransformationHelper()
        // def complexValue = javaHelper.calculateComplexValue(inputPayload.someField)
        // output.processed_value = complexValue
        ```
*   **Registering New Script Context Variables:**
    *   The `GroovyScriptExecutor` can be enhanced to bind additional services or helper objects to the script context if needed (e.g., a client for a data enrichment service).

### 6.5. Error Handling

*   **Detection:**
    *   **Script Compilation Errors:** Caught when loading/reloading scripts. Logged critically. Invalid scripts will prevent the affected rules from being executable.
    *   **Missing Fields/NullPointers:** Groovy's null-safe operator (`?.`) and careful coding in scripts can mitigate some of these. Otherwise, they result in runtime exceptions.
    *   **Type Mismatches:** If a script expects a certain data type and receives another, a `ClassCastException` or similar may occur.
    *   **Script Execution Errors:** Any unhandled exception within the Groovy script.
    *   **Rule Not Found:** If a configured rule ID does not correspond to a loaded script.
*   **Logging:**
    *   All errors will be logged via the **Monitoring and Logging Module**, including `requestId`, `sourceInfo`, rule ID, and detailed error messages/stack traces.
*   **Handling Strategy:**
    1.  When a transformation script execution fails for a `NormalizedDataEvent`:
        *   The `TransformationOrchestrator` catches the exception.
        *   The error is logged with details.
        *   A `TransformedDataEvent` is created with `status = FAILURE`. The `transformationErrors` list will contain details of the error. The `transformedPayload` might be null or the last successfully transformed state (if chaining).
    2.  The **Core Processing Pipeline/Orchestrator** receives this failed `TransformedDataEvent`.
    3.  Based on its configuration for the specific source or error type, the Core Orchestrator will decide the next action:
        *   **Dead-Letter Queue (DLQ):** Send the original `NormalizedDataEvent` (or the failed `TransformedDataEvent`) to a configured DLQ (e.g., a Kafka topic, a specific file system location) for later analysis. This is the preferred approach for asynchronous flows.
        *   **Error Response:** For synchronous interactions (e.g., HTTP API call), propagate an appropriate error response back to the client (e.g., HTTP 500 Internal Server Error with a generic error message or a traceable error ID).
        *   **Skip/Ignore:** In rare cases, if the error is deemed non-critical for a particular flow, the event might be dropped (with logging).

### 6.6. Performance Considerations

*   **Script Compilation/Execution Overhead:**
    *   **Groovy Compilation:** `GroovyShell` parsing and compilation of scripts has an initial overhead. This can be mitigated by:
        *   Caching compiled `Script` objects in the `TransformationRuleRegistry`.
        *   Potentially pre-compiling scripts at build time if dynamic updates are not strictly necessary for all rules.
    *   **Execution:** Groovy, while performant for a dynamic language on the JVM, is generally slower than native Java code.
*   **Reflection:** Groovy's dynamic nature heavily uses reflection, which can have a performance cost.
*   **Large Payloads:** Transformations on very large JSON/Map structures can consume significant memory and CPU.
*   **Complex Logic:** Highly complex calculations or deeply nested iterations within scripts will impact performance.

*   **Optimization Strategies:**
    1.  **Caching Compiled Scripts:** Already mentioned; crucial.
    2.  **Use of Static Compilation (`@CompileStatic`):** For performance-critical Groovy scripts or parts of scripts, use Groovy's `@CompileStatic` or `@TypeChecked` annotations. This allows Groovy to generate bytecode more similar to Java, reducing dynamic dispatch overhead. This sacrifices some dynamic capabilities but significantly improves performance.
    3.  **Java Helpers for Intensive Operations:** For operations that are computationally expensive or require maximum performance (e.g., complex numerical calculations, transformations on very large datasets that can be modeled as POJOs), implement them as Java utility methods and call them from Groovy.
    4.  **Efficient Data Structures:** Encourage script writers to use appropriate data structures and algorithms.
    5.  **Avoid Unnecessary Work:** Ensure scripts only process what's necessary.
    6.  **Resource Limits/Timeouts:** Consider implementing timeouts for script execution to prevent runaway scripts from impacting gateway stability, although this adds complexity.
    7.  **Offloading for Very Large Transformations:** If transformations are extremely heavy (e.g., large batch data transformations), consider if this gateway is the right place or if they should be offloaded to a dedicated batch processing system. The gateway can then act as a router/trigger for these systems.
    8.  **Streaming for Large Payloads (if applicable):** If the input payload can be processed as a stream (e.g., a large JSON array or a series of records), and transformations can be applied record by record, this could reduce memory footprint. However, this makes scripting more complex. The current model assumes the `parsedPayload` is largely in memory.
    9.  **Monitoring and Profiling:** Continuously monitor transformation times. Use profiling tools if specific transformations become bottlenecks to identify areas for optimization (e.g., moving parts to Java).

## 7. Detailed Design: Intelligent Rate Limiting and Routing Layer

This layer takes the `TransformedDataEvent` (output from the Transformation Engine) and applies rate limiting policies, then routes the event to its next destination based on configured rules.

**Input:** `TransformedDataEvent` object. This includes the `transformedPayload`, `payloadType`, `sourceInfo`, `requestId`, and other metadata.

**Output:**
*   For rate limiting: A decision (proceed, reject/HTTP 429, queue).
*   For routing: A `RoutedEvent` object (or similar) specifying the chosen destination (e.g., Kafka topic, HTTP endpoint) and the original `TransformedDataEvent` or its payload.

```java
// Illustrative Java-like class definition
public class RoutedEvent {
    private String requestId;
    private SourceInfo sourceInfo;
    private Object payloadToRoute; // Could be TransformedDataEvent.transformedPayload or the whole event
    private PayloadType payloadType;
    private RouteDestination destination;
    private Map<String, String> routingMetadata; // e.g., specific headers for HTTP egress

    // Getters and Setters
}

public class RouteDestination {
    private DestinationType type; // KAFKA_TOPIC, HTTP_ENDPOINT, GRPC_SERVICE, INTERNAL_QUEUE
    private String target; // e.g., "orders-processed-topic", "http://downstream-service/api/data", "OrderService/ProcessOrder"
    private Map<String, String> properties; // e.g., Kafka producer properties, HTTP method

    // Getters and Setters
}

public enum DestinationType { KAFKA_TOPIC, HTTP_ENDPOINT, GRPC_SERVICE, INTERNAL_QUEUE, DISCARD }
```

### 7.1. Rate Limiting Design

Rate limiting is crucial for protecting the gateway and downstream systems from overload.

#### 7.1.1. Technologies

*   **Primary Library:** **Bucket4j**
    *   **Justification:** Bucket4j is a powerful Java library specifically designed for rate limiting based on the token bucket algorithm. It offers:
        *   **Flexibility:** Supports various rate limiting strategies (fixed window, sliding window, etc. via token bucket configuration).
        *   **High Performance:** Designed for low overhead in high-throughput applications.
        *   **In-Memory (Per-Instance) & Distributed:** Supports in-memory rate limiting by default, which is the focus for this design phase. It also has excellent support for distributed rate limiting with JCache (JSR107) compatible backends like Redis, Hazelcast, etc., which is crucial for future global rate limiting.
        *   **Granular Control:** Allows defining multiple buckets with different bandwidths and for different keys.
*   **Resilience4j Consideration:**
    *   Resilience4j also provides a `RateLimiter` module. However, its primary strength lies in being a comprehensive fault tolerance library (Circuit Breaker, Retry, Bulkhead, etc.). While its rate limiter is functional, Bucket4j is more specialized and feature-rich for complex rate limiting scenarios.
    *   Resilience4j could be used elsewhere in the gateway for other fault tolerance patterns (e.g., circuit breaking for external enrichment calls in the Transformation Engine or for egress calls).

**Decision:** Use **Bucket4j** for its specialized features and performance in rate limiting.

#### 7.1.2. Strategies & Configuration

Rate limits will be applied based on various identifiers.

*   **Dynamic Rate Limiting Implementation:**
    *   A `RateLimiterRegistry` (custom component) will manage different `Bucket` instances from Bucket4j.
    *   For each incoming `TransformedDataEvent`, a **composite key** will be constructed based on configured criteria to identify which bucket to use.
    *   **Key Construction Examples:**
        *   API Key: `metadata.get("X-API-Key")`
        *   Client ID: `sourceInfo.sourceAddress` (e.g., IP address) or a `clientId` field extracted from the payload/metadata.
        *   Source Identifier: `sourceInfo.requestTarget` (e.g., Kafka topic, HTTP endpoint).
        *   Message Priority: A `priority` field that might be set during transformation (e.g., "HIGH", "NORMAL", "LOW"). This would map to different buckets.
        *   Combination: `metadata.get("X-API-Key") + "_" + sourceInfo.requestTarget`

*   **Rule Definition and Management:**
    *   Rate limiting rules will be defined in a configuration file (e.g., `rate_limits.yaml`) managed by the **Configuration Management Module**.
    *   This configuration will define named rate limit profiles and how they map to identifiers.

    ```yaml
    # Example: rate_limits.yaml
    rateLimits:
      profiles:
        - name: "strict_per_ip"
          limit: 10 # permits
          windowSeconds: 1 # time window
          # Bucket4j specific: e.g., greedy token refill
        - name: "api_key_default"
          limit: 100
          windowSeconds: 60
        - name: "high_priority_source"
          limit: 1000
          windowSeconds: 1
          # Potentially more Bucket4j specific configs: initial capacity, refill strategy

      assignments:
        # Default for all sources if no specific match
        - type: "default"
          profile: "api_key_default" # Requires an API key to be present, otherwise fallback or reject
          identifierExpression: "'DEFAULT_KEY_IF_API_KEY_MISSING:' + metadata.getOrDefault('X-API-Key', 'anonymous')"

        - type: "sourceAddress" # Based on client IP
          # sourcePattern: "*" # Apply to all source IPs, or specific IPs/ranges
          profile: "strict_per_ip"
          # Identifier expression would be just the sourceAddress itself
          identifierExpression: "sourceInfo.sourceAddress"

        - type: "apiKey"
          # No sourcePattern needed if applying to all API keys by default
          profile: "api_key_default"
          identifierExpression: "metadata.get('X-API-Key')" # Uses the API key as the bucket key

        - type: "sourceTarget"
          targetPattern: "kafka://high-volume-topic"
          profile: "high_priority_source"
          identifierExpression: "'SOURCE_TARGET:' + sourceInfo.requestTarget" # Bucket per specific high-volume topic

        - type: "messagePriority" # Assumes 'priority' field exists in transformedPayload or metadata
          priorityValue: "HIGH"
          profile: "high_priority_source"
          identifierExpression: "'PRIORITY:' + transformedPayload.priority + ':' + metadata.getOrDefault('X-API-Key', 'anonymous')"
    ```
    *   **Identifier Expression:** A simple expression language (e.g., using Spring Expression Language - SpEL, or a custom one) could be used to extract/construct the key for the bucket from the `TransformedDataEvent`.
    *   The `RateLimiterRegistry` will parse this configuration and create/cache `Bucket` instances. For each unique identifier value derived from `identifierExpression`, a new bucket is provisioned according to the specified profile.

*   **Behavior upon Exceeding Limits:**
    *   **HTTP 429 Response:** For synchronous requests (e.g., HTTP), if the rate limit is exceeded, the layer will signal to the **Core Processing Pipeline/Orchestrator** to return an HTTP 429 "Too Many Requests" response. Retry-After headers can be included if the bucket configuration allows predicting when tokens will be available.
    *   **Message Dropping:** For asynchronous flows (e.g., Kafka), if limits are exceeded, the message might be dropped. This action must be logged extensively.
    *   **Logging:** All rate limiting actions (permit consumed, limit exceeded) will be logged with `requestId`, identifier, and rate limit profile applied.
    *   **Queueing (Advanced):** While not primary for this phase, a limited-size internal queue could temporarily hold requests that exceed the limit, with a short timeout. This can smooth out very short bursts but adds complexity and risk of queue buildup.

#### 7.1.3. Scope

*   **Per-Instance (Default):** The primary design will focus on rate limiting applied per instance of the Data Gateway. Bucket4j's in-memory capabilities are well-suited for this. Each gateway instance maintains its own set of token buckets.
*   **Global Rate Limiting Considerations (Future):**
    *   For true global rate limiting (across all instances of the Data Gateway), a distributed state store is required.
    *   **Redis:** Bucket4j has excellent support for Redis via JCache integrations (e.g., using Redisson). This would involve storing token bucket states in Redis.
    *   **Challenges:** Increased latency due to network calls to Redis, dependency on Redis availability.
    *   This is a future enhancement. The configuration structure should be designed to potentially accommodate global limits later (e.g., a `scope: global` flag in profiles).

### 7.2. Routing Design

After a message has passed rate limiting (or if rate limiting is configured after routing for certain scenarios), the routing logic determines its next destination.

#### 7.2.1. Input Analysis for Routing Keys

The `TransformedDataEvent` is inspected for routing keys. These keys can be:
*   **Specific Fields:** Values from known fields in the `transformedPayload` (if it's a `Map` or POJO) or `metadata`.
    *   Examples: `transformedPayload.logId`, `transformedPayload.customer.userId`, `transformedPayload.eventType`, `metadata.get("X-Route-To")`.
*   **Content Patterns:** Regex matching against parts of the payload (if it's text-based like JSON string, XML string). This is more complex and generally less performant than direct field access.
*   **Source Information:** `sourceInfo.protocol`, `sourceInfo.requestTarget`.
*   **`payloadType`:** The type of the transformed payload.

Routing key extraction will use a similar expression mechanism as rate limiting identifiers (e.g., SpEL) for flexibility.

#### 7.2.2. Routing Logic Engine

*   **Rule-Based Engine:** A custom `RouterComponent` will implement the rule-based engine.
*   **Rule Definition:** Routing rules will be defined in a configuration file (e.g., `routing_rules.yaml`) managed by the **Configuration Management Module**. Rules consist of a condition (or multiple conditions) and a destination. Rules are evaluated in order; the first matching rule determines the destination.

    ```yaml
    # Example: routing_rules.yaml
    routingRules:
      - name: "route_high_priority_orders"
        condition: "transformedPayload.category == 'orders' && transformedPayload.priority == 'HIGH'"
        destination:
          type: "KAFKA_TOPIC"
          target: "high-priority-orders-topic"
          # Kafka producer properties (e.g., acks, retries) can be specified here or globally
      - name: "route_user_logs_by_region"
        condition: "transformedPayload.type == 'user_log' && transformedPayload.user.region != null"
        # Dynamic target based on payload content
        destination:
          type: "KAFKA_TOPIC"
          targetExpression: "'user-logs-' + transformedPayload.user.region.toLowerCase() + '-topic'"
      - name: "route_to_specific_http_service"
        condition: "sourceInfo.requestTarget == 'kafka://partnerA-topic'"
        destination:
          type: "HTTP_ENDPOINT"
          target: "http://partnerA-processor/ingest"
          properties:
            method: "POST"
            # Headers can be added/modified here
            # httpHeaders:
            #   X-Source-System: "Gateway"
      - name: "route_general_events_to_batch_queue"
        condition: "transformedPayload.eventType == 'general_event'"
        destination:
          type: "INTERNAL_QUEUE" # For internal batching/processing
          target: "batch-processing-queue"
      - name: "discard_debug_logs_in_prod"
        condition: "transformedPayload.level == 'DEBUG' && environment == 'PROD'" # 'environment' could be a global config
        destination:
          type: "DISCARD" # Special destination to drop the message
        # Default route if no other rules match
      - name: "default_route_all_else"
        condition: "true" # Always true, acts as a catch-all
        destination:
          type: "KAFKA_TOPIC"
          target: "default-unrouted-events-topic"
    ```
    *   `condition`: An expression (e.g., SpEL) evaluated against the `TransformedDataEvent` (payload, metadata, sourceInfo).
    *   `destination.targetExpression`: Allows dynamic target names based on payload content.

*   **Rule Management and Updates:**
    *   Rules are loaded at startup by the `RouterComponent`.
    *   Dynamic updates are possible if the **Configuration Management Module** can notify the `RouterComponent` of changes to `routing_rules.yaml`, which would then reload and re-evaluate its routing table.

#### 7.2.3. Routing Destinations

*   **Targets:**
    *   **Kafka Topics:** Send data to specific Kafka topics. `RouteDestination.properties` can include Kafka producer settings (e.g., specific serializers, partition key expression).
    *   **HTTP/gRPC Backend Services:** Forward data to external HTTP or gRPC services. `RouteDestination.properties` would include URL, HTTP method, headers, gRPC service/method names.
    *   **Internal Queues:** Place data into internal application queues (e.g., `java.util.concurrent.BlockingQueue` or Spring Integration channels) for further asynchronous processing, batching, or specialized handling within the gateway.
    *   **Discard:** A special destination to explicitly drop messages that meet certain criteria.
*   **Destination Specification:** As defined in the `routing_rules.yaml` under the `destination` block. These definitions are parsed by the `RouterComponent`. For external destinations like Kafka/HTTP/gRPC, the actual sending logic will be delegated to appropriate client/producer components (often managed by the Egress part of the Protocol Adaptation Layer or dedicated egress components).

#### 7.2.4. Technology Consideration

*   **Custom Dispatcher/Router Component:**
    *   A Spring-managed bean (e.g., `@Component class DataRouter`) will encapsulate the routing logic.
    *   It will use SpEL (Spring Expression Language) for evaluating conditions and target expressions due to its good integration with the Spring ecosystem and powerful features.
    *   For actual dispatching to external systems (Kafka, HTTP), this component will likely interact with specific client beans (e.g., `KafkaTemplate`, `WebClient`) rather than directly managing Netty channels. This aligns with a higher-level application flow.

### 7.3. Integration and Order of Operations

A flexible approach is often best:

1.  **Initial Coarse-Grained Rate Limiting (Optional but Recommended):**
    *   Apply a general rate limit based on source IP or a global limit for the entire gateway instance *before* any complex processing like routing or intensive transformations. This provides an early defense against floods.
    *   **Placement:** This would ideally happen very early, possibly even in the Protocol Adaptation Layer or as the very first step in the Core Processing Pipeline. For this section, we assume the main rate limiting happens *after* transformation.

2.  **Main Rate Limiting (as designed in 7.1):**
    *   This occurs after the Transformation Engine has processed the data. The `TransformedDataEvent` is available, allowing rate limiting based on transformed content or priority.

3.  **Routing (as designed in 7.2):**
    *   Occurs *after* the main rate limiting. Once a message is permitted, its destination is determined.

4.  **Per-Route/Destination Rate Limiting (Optional Advanced):**
    *   After a route is determined, an additional, finer-grained rate limit could be applied specific to that destination (e.g., "don't send more than 100 msgs/sec to Partner X's HTTP endpoint").
    *   This adds complexity but offers very granular control. The `RateLimiterRegistry` would need to support buckets keyed by route destination identifiers.

**Recommended Order for this Layer's Design:**

For the "Intelligent Rate Limiting and Routing Layer" itself, the order is:
1.  **Rate Limiting:** Apply rate limits based on `TransformedDataEvent` content and source/API key metadata.
2.  **Routing:** If permitted by the rate limiter, route the event.

This ensures that resources are not wasted on routing messages that would be rate-limited anyway. However, if routing decisions influence which rate limit to apply (e.g., different rate limits for "high-priority-route" vs "low-priority-route"), then:
1.  **Pre-Routing Rate Limiting (Coarse):** Optional, very basic.
2.  **Routing Decision:** Determine the route.
3.  **Post-Routing Rate Limiting (Fine-grained):** Apply rate limits specific to the chosen route or based on priority derived from routing.

The design using `rate_limits.yaml` assignments allows flexibility: some rate limits can be general, while others can be specific to certain `sourceTarget`s or `messagePriority` (which could be set based on an initial routing decision if the pipeline were structured that way). For simplicity in this iteration, we'll assume rate limiting happens based on the incoming `TransformedDataEvent` before the final routing destination is selected by this layer.

### 7.4. Dynamic Configuration

*   **Rate Limiting Thresholds:**
    *   Bucket4j configurations (bandwidths) can be updated at runtime.
    *   The **Configuration Management Module** would monitor `rate_limits.yaml`. Upon changes, it would notify the `RateLimiterRegistry`.
    *   The `RateLimiterRegistry` would then reconfigure or replace the affected `Bucket` instances. Bucket4j's `ProxyManager` can help manage buckets dynamically.
*   **Routing Rules:**
    *   Similarly, the `RouterComponent` would be notified of changes to `routing_rules.yaml` by the Configuration Management Module.
    *   The `RouterComponent` would then rebuild its internal representation of the routing rules (e.g., the ordered list of conditions and destinations).
*   **Feasibility:** This is feasible with careful implementation, ensuring thread safety and atomicity during updates. Libraries like Apache Commons Configuration or Spring Cloud Config can assist with dynamic configuration reloading.

### 7.5. Error Handling

*   **Rate Limiter Errors:**
    *   **Failure to Evaluate Identifier Expression:** If SpEL (or other expression language) fails to parse/evaluate the `identifierExpression` in `rate_limits.yaml`. Log critically, potentially fallback to a default restrictive rate limit or reject the request.
    *   **Backend Store Issues (for Global Limits):** If a distributed store like Redis is used and becomes unavailable, Bucket4j can be configured with fallback behavior (e.g., permit all, deny all, or use local in-memory limit). This needs careful consideration based on desired behavior during such outages. Logged with high severity.
*   **Routing Errors:**
    *   **Failure to Evaluate Condition/Target Expression:** If SpEL fails for a routing rule's `condition` or `targetExpression`. Log error, and the message will likely not match the problematic rule, falling through to the next rule or the default route. If the default route also fails, it's a critical issue.
    *   **Unreachable Route/Destination Misconfiguration:** If a rule points to a Kafka topic that doesn't exist or an HTTP endpoint that is misconfigured (but the endpoint itself is down, that's an egress issue, not a routing rule error). Log error. The event should ideally be routed to a "dead-letter" or "undeliverable" destination (e.g., a specific Kafka topic for such cases).
    *   **No Matching Route:** If no rule matches and there's no default/catch-all route. This indicates a configuration gap. The message should be routed to an "unroutable" or dead-letter destination.
*   **General Strategy:**
    *   Log all errors comprehensively via the **Monitoring and Logging Module**.
    *   For critical errors (e.g., inability to load any rate limits or routing rules), the layer might need to fail startup or enter a degraded mode (e.g., reject all requests).
    *   The **Core Processing Pipeline/Orchestrator** will be responsible for handling events that this layer explicitly rejects (e.g., due to rate limiting) or flags as unroutable.

## 8. Detailed Design: Core Processing Pipeline / Orchestrator

This section details the design of the Core Processing Pipeline (often referred to as the Orchestrator). It integrates the previously defined layers to manage the end-to-end flow of data from ingress to egress.

**Primary Goal:** To provide a robust, configurable, observable, and resilient mechanism for processing data through various stages within the Data Gateway.

### 8.1. Pipeline Stages and Data Flow

The pipeline consists of a sequence of stages that process data. The primary data objects evolve as they pass through these stages.

**Sequence of Stages & Data Objects:**

1.  **Protocol Adaptation (Ingress):**
    *   **Input:** Raw request from an external client (e.g., HTTP request, Kafka message bytes).
    *   **Processing:** Handled by the **Protocol Adaptation Layer** (Section 4).
    *   **Output:** `UnifiedInternalRequest` (defined in Section 4.2). This object standardizes the representation of incoming requests, containing the raw payload, source information, and metadata.

2.  **Format Normalization & Validation:**
    *   **Input:** `UnifiedInternalRequest`.
    *   **Processing:** Handled by the **Format Normalization and Conversion Layer** (Section 5). This stage parses the payload, validates it against schemas, and may perform initial conversions (e.g., CSV to an internal representation).
    *   **Output:** `NormalizedDataEvent` (defined in Section 5). This contains the parsed payload in a structured format (e.g., `Map<String, Object>`, Protobuf message), validation results, and potentially a converted payload if a format change occurred (e.g., CSV to Parquet bytes).

3.  **Transformation Engine:**
    *   **Input:** `NormalizedDataEvent`.
    *   **Processing:** Handled by the **Transformation Engine** (Section 6). This stage applies complex data manipulations, mapping, and enrichment logic using configured rules (e.g., Groovy scripts).
    *   **Output:** `TransformedDataEvent` (defined in Section 6). This contains the transformed payload, its type, and the status of the transformation.

4.  **Intelligent Rate Limiting & Routing (Combined Stage):**
    *   This design combines rate limiting and routing for simplicity, as their configurations are often intertwined and both operate on the `TransformedDataEvent`.
    *   **Input:** `TransformedDataEvent`.
    *   **Processing:** Handled by the **Intelligent Rate Limiting and Routing Layer** (Section 7).
        *   **Rate Limiting:** Applies configured rate limits. If exceeded, the pipeline might terminate here for this event (e.g., by generating an error response or sending to a DLQ).
        *   **Routing:** If permitted by the rate limiter, routing rules are evaluated to determine the destination(s).
    *   **Output (if proceeding):** `RoutedEvent` (defined in Section 7). This specifies the chosen destination(s) and the payload to be dispatched.

5.  **Egress/Dispatch:**
    *   **Input:** `RoutedEvent`.
    *   **Processing:** This stage is responsible for sending the payload to the destination specified in the `RoutedEvent`. It utilizes client components that are part of, or managed by, the **Protocol Adaptation Layer** (for egress capabilities) or dedicated egress service clients.
        *   Examples: Kafka producer sends to a Kafka topic, HTTP client makes a POST request, gRPC client calls a remote service.
    *   **Output:** `EgressProcessingResult` (or similar). This object indicates the outcome of the dispatch operation.
        ```java
        // Illustrative Java-like class definition
        public class EgressProcessingResult {
            private String requestId;
            private RouteDestination destination; // The intended destination
            private EgressStatus status;          // SUCCESS, FAILURE, RETRYABLE_FAILURE
            private String failureReason;       // If status is FAILURE
            private int attemptCount;           // Number of attempts made
            private Map<String, String> responseMetadata; // e.g., HTTP response headers from downstream

            // Getters and Setters
        }
        public enum EgressStatus { SUCCESS, FAILURE, RETRYABLE_FAILURE }
        ```

**Data Flow Diagram (Conceptual):**

```
External Request --> [1. Protocol Adapter (Ingress)] --UnifiedInternalRequest-->
                     [2. Format Normalizer/Validator] --NormalizedDataEvent-->
                     [3. Transformation Engine] --TransformedDataEvent-->
                     [4. Rate Limiter & Router] --RoutedEvent (if permitted)-->
                     [5. Egress Dispatcher] --EgressProcessingResult--> External System / Internal Log
```

### 8.2. Orchestration and Control Flow

**Orchestration Mechanism: Reactive Streams (Project Reactor / Spring WebFlux)**

Given the gateway's need for high throughput, non-blocking I/O, and effective backpressure management, a reactive streams approach is highly recommended. Spring WebFlux, built on Project Reactor, provides a robust framework for this.

*   **Central Orchestrator Service (`GatewayPipelineOrchestrator`):**
    *   This service will be the main entry point after the Protocol Adapters have created the `UnifiedInternalRequest`.
    *   It will define the pipeline as a chain of reactive operations (e.g., `Mono` or `Flux` transformations).
    *   Each stage (Normalization, Transformation, Rate Limiting/Routing, Egress) will be implemented as a component that returns a `Mono<OutputEvent>` given an `InputEvent`.

    ```java
    // Illustrative Orchestrator Logic (conceptual)
    @Service
    public class GatewayPipelineOrchestrator {
        // Autowire components for each stage
        private final FormatNormalizationService normalizationService;
        private final TransformationService transformationService;
        private final RateLimitingRoutingService rateLimitingRoutingService;
        private final EgressService egressService;

        public Mono<Void> processRequest(Mono<UnifiedInternalRequest> requestMono) {
            return requestMono
                .flatMap(normalizationService::normalize) // Mono<NormalizedDataEvent>
                .flatMap(transformationService::transform) // Mono<TransformedDataEvent>
                .flatMap(rateLimitingRoutingService::checkAndRoute) // Mono<RoutedEvent>
                .flatMap(egressService::dispatch) // Mono<EgressProcessingResult>
                .doOnSuccess(result -> log.info("Request {} processed successfully to {}", result.getRequestId(), result.getDestination()))
                .doOnError(error -> log.error("Pipeline error for request: {}", error.getMessage())) // Simplified error logging
                .then(); // Returns Mono<Void> indicating completion or error
        }

        // For synchronous responses (e.g. HTTP)
        public Mono<ResponseEntity<String>> processHttpRequest(Mono<UnifiedInternalRequest> requestMono) {
            return requestMono
                .flatMap(normalizationService::normalize)
                .flatMap(transformationService::transform)
                .flatMap(rateLimitingRoutingService::checkAndRoute) // This might immediately signal a 429
                .flatMap(egressService::dispatch) // Egress for HTTP might involve getting a response
                .map(egressResult -> {
                    if (egressResult.getStatus() == EgressStatus.SUCCESS) {
                        // Potentially use egressResult.responseMetadata for HTTP response
                        return ResponseEntity.ok("Request processed");
                    } else {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed: " + egressResult.getFailureReason());
                    }
                })
                .onErrorResume(RateLimitExceededException.class, ex ->
                    Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ex.getMessage()))
                )
                .onErrorResume(ValidationException.class, ex ->
                    Mono.just(ResponseEntity.badRequest().body("Validation failed: " + ex.getMessage()))
                )
                .onErrorResume(TransformationException.class, ex ->
                    Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Transformation error: " + ex.getMessage()))
                )
                .onErrorResume(RoutingException.class, ex ->
                    Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Routing error: " + ex.getMessage()))
                )
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unknown error"));
        }
    }
    ```

*   **Maintaining Processing Context:**
    *   **Request ID:** The `requestId` generated by the Protocol Adaptation Layer (or propagated from the source) will be carried through all intermediate data objects (`UnifiedInternalRequest`, `NormalizedDataEvent`, etc.). This is crucial for logging and tracing.
    *   **Metadata:** Key metadata (e.g., security tokens, original source headers) can be passed in the `metadata` map within these objects. Each stage should only read what it needs and pass the rest along or add new metadata if necessary.
    *   **Reactive Context:** Project Reactor's `Context` can be used to pass contextual information implicitly through the reactive chain, although explicitly passing it via event objects is often clearer for data processing pipelines.

### 8.3. Asynchronous Processing and Backpressure

*   **Asynchronous Handoffs:**
    *   **Protocol Adapters (Ingress):** Already designed to be largely asynchronous (e.g., WebFlux for HTTP, Kafka consumers in separate threads).
    *   **Before Transformation Engine:** If transformations are particularly heavy or involve I/O (e.g., external enrichment calls), an asynchronous handoff can decouple the normalization stage from transformation.
        *   **Mechanism:** An internal, in-memory `BlockingQueue` (e.g., `LinkedBlockingQueue`) wrapped as a `Flux` or a dedicated Schedulers (e.g., `Schedulers.boundedElastic()` for I/O-bound tasks in Reactor) for the transformation stage.
    *   **Before Egress/Dispatch:** Egress operations are inherently I/O-bound. Reactive clients (e.g., `WebClient` for HTTP, reactive Kafka producers) handle this naturally. If not using reactive clients, tasks should be submitted to a dedicated thread pool (e.g., via `Schedulers.boundedElastic()`).

*   **Backpressure Strategies:**
    *   **Reactive Streams:** Project Reactor inherently supports backpressure. Downstream consumers signal demand to upstream producers. This is the primary mechanism throughout the pipeline if built with Reactor.
    *   **Internal Queues:** If using `BlockingQueue`s for handoffs, their bounded nature provides natural backpressure. If a queue is full, the producing stage will block (or fail, depending on `offer` vs `put`), signaling backpressure. Queue sizes must be carefully configured and monitored.
    *   **Rate Limiting:** The Rate Limiting layer itself is a form of explicit backpressure, preventing the gateway from accepting or processing more data than configured limits allow.
    *   **Thread Pools:** For non-reactive, blocking I/O operations, using bounded thread pools (e.g., `Schedulers.boundedElastic()` creates these) prevents unbounded thread creation and resource exhaustion.

### 8.4. Comprehensive Error Handling Strategy

*   **Centralized Error Handling Points:**
    *   The main `GatewayPipelineOrchestrator`'s reactive chain will use operators like `onErrorResume`, `onErrorMap`, `doOnError` to catch errors from any stage.
    *   Specific error handling logic can be applied based on the type of exception or the stage where it occurred.

*   **Error Reporting:**
    *   Errors are encapsulated in the respective event objects (`NormalizedDataEvent.validationResult`, `TransformedDataEvent.transformationErrors`, `EgressProcessingResult.failureReason`).
    *   The Orchestrator logs these errors centrally.

*   **Retry Mechanisms:**
    *   **For Transient Errors:** Applicable mainly for egress operations (e.g., temporary network issue sending to a downstream service) or external enrichment calls in the Transformation Engine.
    *   **Technology:** Project Reactor provides retry operators (`retryWhen`, `retryBackoff`). Resilience4j's `Retry` module can also be integrated for more sophisticated retry strategies.
    *   **Configuration:** Retry policies (number of attempts, backoff strategy - exponential, fixed) will be configurable per destination or type of operation, managed by the **Configuration Management Module**.
        ```yaml
        # Example: retry_policies.yaml
        retryPolicies:
          - name: "default_egress_retry"
            maxAttempts: 3
            backoff:
              delayMillis: 1000
              multiplier: 2.0
              maxDelayMillis: 30000
            retryableExceptions: # List of fully qualified exception class names
              - "java.net.ConnectException"
              - "java.util.concurrent.TimeoutException"
              - "com.example.gateway.RetryableEgressException" # Custom exception
        ```
    *   The `EgressService` (or similar) would consult these policies.

*   **Dead-Letter Queues (DLQs):**
    *   **For Irrecoverable Errors:** When processing fails definitively at any stage (e.g., persistent validation failure, non-transient transformation error, all retry attempts exhausted for egress).
    *   **Mechanism:** The `GatewayPipelineOrchestrator`, upon catching an irrecoverable error or an `EgressStatus.FAILURE` after retries, will construct a DLQ message.
    *   **DLQ Message Content:**
        *   `requestId`
        *   `originalSourceInfo` (from `UnifiedInternalRequest`)
        *   `failedStage`: Name of the stage where the error occurred (e.g., "Normalization", "Transformation", "Egress:http://target/api").
        *   `timestampOfFailure`
        *   `errorDetails`: Exception type, message, stack trace (potentially summarized).
        *   `originalPayload`: The payload as it was received by the failing stage (or the initial `UnifiedInternalRequest.payload`).
        *   `metadataAtFailure`: Relevant metadata available at the point of failure.
    *   **DLQ Target:** Typically a dedicated Kafka topic (e.g., `gateway-dlq-topic`), but could be a file system location or another messaging system, configured via the **Configuration Management Module**.
        ```yaml
        # Example: error_handling.yaml
        errorHandling:
          dlq:
            type: "KAFKA" # or FILE, etc.
            target: "gateway-main-dlq-topic"
            # Kafka producer properties for DLQ
          # Define which errors trigger DLQ, or if all pipeline failures do
        ```

*   **Synchronous Error Responses (e.g., HTTP):**
    *   As shown in the `processHttpRequest` example in Section 8.2.
    *   The `GatewayPipelineOrchestrator` uses `onErrorResume` to catch specific custom exceptions thrown by stages (e.g., `ValidationException`, `RateLimitExceededException`, `TransformationException`, `EgressException`).
    *   These exceptions are then mapped to appropriate HTTP status codes (400, 429, 500, 502, etc.) and a meaningful error response body (often JSON, including `requestId` for traceability).
    *   If an error doesn't map to a specific exception, a generic HTTP 500 response is returned.

### 8.5. Logging, Tracing, and Monitoring (Cross-Cutting Concerns)

These are managed by the **Monitoring and Logging Module** but integrated by the pipeline.

*   **Logging:**
    *   **Points:**
        *   Entry and exit of each pipeline stage.
        *   Significant decisions (e.g., rule matched in router, rate limit applied/exceeded).
        *   All errors, including full context.
        *   Successful completion of the entire pipeline for a request.
    *   **Structure (JSON formatted for easier parsing):**
        ```json
        {
          "timestamp": "YYYY-MM-DDTHH:mm:ss.SSSZ",
          "level": "INFO", // or ERROR, WARN, DEBUG
          "requestId": "unique-request-id",
          "traceId": "opentelemetry-trace-id", // If tracing enabled
          "spanId": "opentelemetry-span-id",   // If tracing enabled
          "stage": "TransformationEngine", // or Normalization, Routing, Egress:http://xyz
          "message": "Transformation rule 'map_order_v1' applied successfully.",
          "sourceTarget": "kafka://input-topic", // From sourceInfo
          "payloadType": "JSON",
          // Other relevant context: e.g., ruleId, validationErrorsCount, destinationTopic
        }
        ```
    *   **Implementation:** Using SLF4J with Logback (or Log4j2). MDC (Mapped Diagnostic Context) will be used to automatically include `requestId`, `traceId`, and `spanId` in every log message.

*   **Distributed Tracing:**
    *   **Technology:** OpenTelemetry (OTel) SDK.
    *   **Integration:**
        *   Protocol Adapters (Ingress): Start a new trace or continue an existing trace from incoming headers (e.g., W3C Trace Context, B3). A root span for the gateway processing is created.
        *   Each Pipeline Stage: Create a child span for processing within that stage. Key attributes (e.g., rule ID, validation outcome, destination) are added to spans.
        *   Context Propagation: OTel context (containing `traceId`, `spanId`) is propagated through the reactive chain using Reactor's `Context` or via OTel's instrumentation for common libraries (e.g., Kafka clients, HTTP clients).
        *   Protocol Adapters (Egress): Inject trace context into outgoing requests to allow downstream services to continue the trace.
    *   **Export:** Traces exported to a compatible backend (e.g., Jaeger, Zipkin, OpenTelemetry Collector).

*   **Monitoring (Metrics):**
    *   **Technology:** Micrometer library (abstracts metrics backends like Prometheus).
    *   **Key Metrics:**
        *   **Overall Pipeline:**
            *   `gateway_requests_total{protocol, sourceTarget, status}` (status: success, failed_validation, failed_transformation, rate_limited, failed_egress) - Counter
            *   `gateway_requests_latency_seconds{protocol, sourceTarget}` - Histogram/Summary
        *   **Per Stage:**
            *   `gateway_stage_latency_seconds{stageName, sourceTarget}` - Histogram/Summary
            *   `gateway_stage_errors_total{stageName, errorCode}` - Counter
        *   **Queues (if used):**
            *   `gateway_queue_depth{queueName}` - Gauge
            *   `gateway_queue_tasks_total{queueName, status}` (status: enqueued, dequeued, dropped) - Counter
        *   **Rate Limiting:**
            *   `gateway_ratelimit_acquired_total{profileName}` - Counter
            *   `gateway_ratelimit_throttled_total{profileName}` - Counter
        *   **Egress:**
            *   `gateway_egress_requests_total{destination, status}` - Counter
            *   `gateway_egress_latency_seconds{destination}` - Histogram/Summary
    *   **Exposure:** Metrics exposed via an HTTP endpoint (e.g., `/actuator/prometheus`) for Prometheus to scrape. Visualized in Grafana.

### 8.6. Pipeline Configuration

Configuration is managed by the **Configuration Management Module**. The Core Processing Pipeline itself will need configuration for:

*   **Stage Enablement:**
    *   While core stages (Normalization, Transformation, Routing) are usually always on, flags could exist to bypass certain custom sub-steps if needed for specific flows (though often better handled by routing to a "no-op" transformation).
    *   Example:
        ```yaml
        # pipeline_config.yaml
        pipelines:
          - name: "default_json_pipeline"
            sourcePattern: "kafka://json-*" # Apply to Kafka topics starting with json-
            stages:
              normalization: true
              transformation: true # Can point to specific transformation rule sets
              rateLimiting: true
              routing: true
              egress: true
          - name: "passthrough_binary_pipeline"
            sourcePattern: "mqtt://binary-passthrough/**"
            stages:
              normalization: false # Skip parsing if binary
              transformation: false # Skip transformation
              rateLimiting: true
              routing: true # Route based on MQTT topic
              egress: true
        ```
    *   This allows defining different pipeline behaviors for different types of sources or data. The `GatewayPipelineOrchestrator` would select the appropriate pipeline configuration based on the incoming `UnifiedInternalRequest`.

*   **Timeouts:**
    *   Timeouts between stages or for total pipeline execution (especially for synchronous requests).
    *   Project Reactor's `.timeout(Duration)` operator can be applied at various points in the reactive chain.
    *   Configuration:
        ```yaml
        # Example: pipeline_timeouts.yaml
        timeouts:
          defaultPipelineTimeoutMs: 30000 # For overall synchronous request
          transformationStageTimeoutMs: 5000
          egressHttpTimeoutMs: 10000
        ```

*   **Retry Policies:** As defined in Section 8.4 (e.g., `retry_policies.yaml`).
*   **DLQ Configuration:** As defined in Section 8.4 (e.g., `error_handling.yaml`).
*   **Default Behavior:** Configuration for default routes, default rate limits if specific rules are not matched.

This detailed design for the Core Processing Pipeline provides a framework for integrating all gateway components into a cohesive, manageable, and observable data processing flow.

## 9. Detailed Design: API and Configuration Management

This section details the design of the Configuration Management Module and the Administrative API for the Data Gateway. It covers how various aspects of the gateway are configured, loaded, dynamically updated, and managed at runtime.

### 9.1. Configuration Structure and Format

#### 9.1.1. Primary Format: YAML

*   **Recommendation:** YAML (YAML Ain't Markup Language).
*   **Justification:**
    *   **Human Readability:** YAML is significantly more readable than JSON, especially for complex, nested configurations, due to its use of indentation and minimal syntax (no excessive braces or commas).
    *   **Comments:** YAML supports comments, which are crucial for documenting configuration options directly within the files.
    *   **Structure:** Excellent for representing hierarchical data, which aligns well with the gateway's modular configuration needs.
    *   **Anchors and Aliases:** Supports reusable configuration blocks, reducing redundancy.
    *   **Ecosystem Support:** Widely supported by Java libraries (e.g., Jackson's YAML extension, SnakeYAML).
    *   **Comparison:**
        *   **JSON:** Less readable for large configurations, no comments.
        *   **HOCON (Human-Optimized Config Object Notation):** Powerful, good for overrides and includes, but less universally known than YAML. YAML can achieve similar modularity with includes or by convention.
        *   **Properties files:** Too flat for complex hierarchical data.

#### 9.1.2. Global Configuration Structure

A main configuration file (e.g., `application.yaml` or `data-gateway.yaml`) will define the global structure. This structure will be hierarchical, mirroring the gateway's components.

```yaml
# data-gateway.yaml (Main Configuration File)

server: # General server settings (if applicable, e.g., for admin API)
  port: 8090 # Port for the admin API and management endpoints
  # SSL configuration for admin API can go here

gateway:
  # --- Protocol Adaptation Layer (Section 4) ---
  protocolAdapters:
    http:
      enabled: true
      port: 8080 # Port for data ingestion HTTP endpoints
      # Example: /ingest/{sourceId} -> sourceId used for further config lookup
      # Further specific settings like request size limits, idle timeouts
    grpc:
      enabled: true
      port: 50051
      # serviceDefinitions: # For specific gRPC services if not generic
      #   - protoFile: "path/to/service1.proto" # Informational
      #     messageType: "com.example.Service1Request"
    kafka:
      enabled: true
      defaultClientIdPrefix: "data-gateway-consumer"
      consumers: # List of consumer configurations
        - name: "orders-consumer"
          bootstrapServers: "kafka1:9092,kafka2:9092"
          topics: ["orders-json-topic", "orders-protobuf-topic"]
          groupId: "gateway-orders-group"
          keyDeserializer: "org.apache.kafka.common.serialization.StringDeserializer"
          valueDeserializer: "org.apache.kafka.common.serialization.ByteArrayDeserializer" # Keep as bytes
          # payloadTypeByTopic: # Optional: hint for payload type if not in message header
          #   "orders-json-topic": "JSON"
          #   "orders-protobuf-topic": "PROTOBUF"
          #   "orders-protobuf-topic.messageType": "com.example.OrderProtos.Order" # If specific proto
          autoOffsetReset: "latest"
          # Other Kafka consumer properties (e.g., security, SSL)
    mqtt:
      enabled: false
      defaultClientIdPrefix: "data-gateway-mqtt-client"
      subscribers:
        - name: "iot-subscriber"
          brokerUrl: "tcp://mqtt-broker:1883"
          username: "user" # Use environment variables or secrets management for passwords
          passwordPlaceholder: "${MQTT_IOT_PASSWORD}" # Placeholder for env var
          topics: ["iot/+/data", "sensors/#"] # QoS per topic possible
          qos: 1 # Default QoS for subscribed topics
          # Other MQTT client properties

  # --- Format Normalization and Conversion Layer (Section 5) ---
  formatNormalization:
    json:
      defaultEncoding: "UTF-8"
      # schemaValidation: enabled/disabled globally or per source
    protobuf:
      # Mapping from sourceInfo.requestTarget or metadata to specific Protobuf message types
      # This would be part of the 'pipeline_config.yaml' or a dedicated schema mapping file
      # Example: (in pipeline_config.yaml or schema_mappings.yaml)
      # schemaMappings:
      #  - sourceTarget: "kafka://orders-protobuf-topic"
      #    protobufMessageType: "com.example.OrderProtos.Order"
      #  - sourceTarget: "/ingest/product-updates" # HTTP endpoint
      #    protobufMessageType: "com.example.ProductProtos.ProductUpdate"
      # Protobuf to JSON default printer settings
      protobufToJson:
        preservingProtoFieldNames: true
        includingDefaultValueFields: true
    csv:
      # Default CSV parsing options, can be overridden per source
      default:
        delimiter: ","
        headerPresent: true
        skipHeaderRecord: true
        # quoteChar, escapeChar, nullString, etc.
      # Specific configurations per source can be in 'pipeline_config.yaml' or a CSV specific config file.
    validation: # Generic validation framework rules
      # Rules defined in separate files, e.g., validation_rules/
      # Example: (in validation_rules/order_rules.yaml)
      # - id: "order_amount_check"
      #   appliesTo: # criteria to select this rule
      #     sourceTargetPattern: "kafka://orders-*"
      #   rules:
      #     - type: "fieldConstraint", field: "orderAmount", constraint: "min:0, max:10000"
      # (Actual rule definitions would be more structured per Section 5.3)

  # --- Transformation Engine (Section 6) ---
  transformationEngine:
    scriptPaths: ["classpath:/transformations/", "file:./config/transformations/"] # Search paths for Groovy scripts
    # Rule bindings defined in separate files, e.g., transformation_bindings.yaml (see Section 6.2.2)
    # Global settings for Groovy engine (e.g., compilation caching)
    groovy:
      cacheCompiledScripts: true
      # Sandboxing configuration if scripts are less trusted
      # secureASTCustomizers, etc.

  # --- Intelligent Rate Limiting and Routing Layer (Section 7) ---
  rateLimiting:
    # Definitions in rate_limits.yaml (see Section 7.1.2)
    # Global defaults for Bucket4j if needed
    enabled: true
  routing:
    # Definitions in routing_rules.yaml (see Section 7.2.2)
    # Default route if no rules match
    defaultRoute:
      type: "KAFKA_TOPIC" # Or DISCARD, or specific error topic
      target: "unrouted-events-dlq"

  # --- Core Processing Pipeline / Orchestrator (Section 8) ---
  pipeline:
    # Definitions in pipeline_config.yaml, retry_policies.yaml, error_handling.yaml (see Section 8.6)
    # Global defaults for timeouts
    defaultTimeoutMs: 30000
    # Global DLQ settings if not overridden
    dlq:
      defaultTarget: "global-dlq-kafka-topic"
      defaultType: "KAFKA"

  # --- Egress (if specific global egress configs are needed beyond routing destinations) ---
  egress:
    kafkaProducers: # Default Kafka producer settings for egress
      - name: "default-producer"
        bootstrapServers: "kafka-out:9092"
        keySerializer: "org.apache.kafka.common.serialization.StringSerializer"
        valueSerializer: "org.apache.kafka.common.serialization.ByteArraySerializer"
        # acks, retries, etc.
    httpClients: # Default HTTP client settings for egress
      - name: "default-http-client"
        connectTimeoutMs: 5000
        readTimeoutMs: 10000
        # Default headers, proxy settings

  # --- Logging & Monitoring (Section 8.5) ---
  logging:
    level: # Default log levels, can be overridden by environment variables
      root: "INFO"
      com.example.gateway: "DEBUG" # Specific package level
    # Configuration for structured JSON logging (e.g., Logback appender settings)
    # MDC fields configuration
  monitoring:
    metrics:
      enabled: true
      # Micrometer specific settings, e.g., tags, endpoint path
      # prometheus:
      #   enabled: true
      #   path: "/actuator/prometheus"
    tracing:
      enabled: true
      # OpenTelemetry specific settings: exporter type (jaeger, zipkin, otlp), endpoint, sampling rate
      # otel:
      #   exporter: "otlp"
      #   otlpEndpoint: "http://otel-collector:4317"
      #   samplingRate: 0.1 # Sample 10% of traces

  # --- Security for Admin API ---
  security:
    adminApi:
      authentication: "API_KEY" # Or BASIC_AUTH, OAUTH2_CLIENT_CREDENTIALS
      # For API_KEY:
      # apiKeyHeader: "X-Admin-API-Key"
      # apiKeys: # List of hashed API keys and their roles
      #  - hashedKey: "xxxxx" # Store hashed keys
      #    roles: ["ADMIN", "VIEWER"]
      #  - hashedKey: "yyyyy"
      #    roles: ["VIEWER"]
      # For BASIC_AUTH:
      # users:
      #  - username: "admin"
      #    passwordPlaceholder: "${ADMIN_PASSWORD}" # Hashed or placeholder for env var
      #    roles: ["ADMIN"]
      # Authorization roles needed for specific endpoints (can be fine-grained)
      # Default: require ADMIN role for POST/DELETE, VIEWER for GET on /config, /status etc.
```

#### 9.1.3. Modularity

*   **Includes/Imports:** While YAML itself doesn't have a standard include mechanism like HOCON, this can be achieved by:
    1.  **Convention & Application Logic:** The application can be designed to load multiple YAML files from a specified directory (e.g., `config/`) and merge them. For example, `data-gateway.yaml` for core settings, `kafka_adapters.yaml`, `routing_rules.yaml`, `rate_limits.yaml` in a `conf.d` style directory.
    2.  **Spring Profiles:** If using Spring Boot, its profile-specific YAML files (`application-{profile}.yaml`) naturally allow splitting configurations for different environments. Spring also supports importing additional YAML files using `spring.config.import: ["optional:file:./custom-config.yaml"]`.
*   **Specific Configurations:**
    *   **Schemas (JSON Schema, Protobuf .proto files):** Stored as separate files, paths referenced in YAML.
    *   **Transformation Scripts (Groovy):** Stored as separate `.groovy` files, paths or rule IDs referenced.
    *   **Routing Rules, Rate Limit Profiles, Pipeline Definitions:** As shown in the examples, these are already structured to be potentially in their own files (e.g., `routing_rules.yaml`, `rate_limits.yaml`, `pipeline_config.yaml`) and loaded/merged by the Configuration Management Module.

### 9.2. Configuration Loading and Management

#### 9.2.1. Initial Loading

1.  **Default Configuration:** Load default values from `application.yaml` (or a similar named file) packaged within the application JAR (classpath).
2.  **External Configuration File:** Allow overriding defaults with an external `data-gateway.yaml` file specified by a path (e.g., via command-line argument `--config.file=./config/data-gateway.yaml` or environment variable `CONFIG_FILE_PATH`).
3.  **Environment Variables:** Allow specific configuration properties to be overridden by environment variables (e.g., `GATEWAY_PROTOCOLADAPTERS_HTTP_PORT=8888` would override `gateway.protocolAdapters.http.port`). A clear mapping convention is needed (e.g., uppercase, underscore separated). Secrets (passwords, API keys) **must** be injectable via environment variables or a secrets management system, not hardcoded in files.
4.  **Command-line Arguments:** For simple overrides, e.g., `--server.port=9000`.
    *Spring Boot's configuration loading mechanism handles this order of precedence naturally.*

#### 9.2.2. Externalized Configuration (Consideration for Future)

*   **Spring Cloud Config Server:** Centralizes configuration files in a Git repository or HashiCorp Vault. Gateway instances connect to the Config Server to fetch their configuration.
*   **Consul, etcd:** Key-value stores that can hold configuration data.
*   **Integration:** Requires adding client libraries and specific bootstrap configuration.
*   **Benefits:** Centralized management, versioning (if Git-backed), dynamic updates across multiple instances via mechanisms like Spring Cloud Bus.
*   **Initial Design:** Will focus on local file-based configuration for simplicity, but the structure should be compatible with future externalization.

#### 9.2.3. Dynamic Updates

*   **Mechanism:**
    1.  **Admin API Endpoint:** `POST /admin/config/reload` (see Section 9.3). This is the most explicit and controlled way.
    2.  **File System Polling (Optional):** Monitor the primary configuration file(s) for changes. If a change is detected, trigger a reload. This is convenient for local development but might be less suitable for production if not handled carefully (e.g., partial writes). Libraries like Apache Commons Configuration provide file watching capabilities. Spring Boot can also be configured to reload properties via Actuator's `/refresh` endpoint if using Spring Cloud Config.
    3.  **Spring Cloud Bus (with External Config Server):** For distributed environments, Spring Cloud Bus can broadcast a refresh event to all gateway instances when configuration changes in the central Config Server.

*   **Notification and Reconfiguration:**
    1.  The **Configuration Management Module** is responsible for detecting or being triggered for a reload.
    2.  It re-reads and re-parses the configuration files.
    3.  It validates the new configuration (see Section 9.4). If invalid, the reload is aborted, and the old configuration remains active. An error is logged.
    4.  If valid, the module publishes an internal "ConfigurationChangedEvent" (e.g., using Spring's `ApplicationEventPublisher` or a custom event bus).
    5.  Individual components (Protocol Adapters, Transformation Engine, Router, Rate Limiter Registry, Pipeline Orchestrator) subscribe to this event.
    6.  Upon receiving the event, each component updates its internal state based on the new configuration. This might involve:
        *   Restarting listeners on new ports (Protocol Adapters - complex, might require careful handling or be deferred to instance restart for port changes).
        *   Re-initializing Kafka/MQTT clients if broker details change.
        *   Reloading transformation scripts.
        *   Rebuilding rate limit buckets.
        *   Re-parsing routing rules.
        *   Adjusting pipeline timeouts or retry policies.
*   **Atomicity and Safety:**
    *   **Validation First:** Critical to validate the entire new configuration before applying any part of it.
    *   **Component-Level Atomicity:** Each component should try to update its configuration atomically. If a component fails to reconfigure, it should ideally roll back to its previous working configuration or clearly signal an error state.
    *   **Global Atomicity (Challenging):** True atomicity across all components for all types of changes is very difficult without a full "commit/rollback" transaction across the application state. The strategy is often to:
        *   Make components resilient to transient misconfigurations during updates.
        *   Log errors extensively.
        *   For highly critical changes (e.g., changing a fundamental port that requires a socket rebind), a service restart might be safer and simpler than attempting a complex live update. The dynamic reload feature should clearly document which changes are "hot-reloadable" versus those requiring a restart.
        *   Phased application: Apply changes to a subset of components first, then others.

### 9.3. Administrative API (Management Endpoints)

Exposed on the `server.port` defined in the configuration, and secured. Spring Boot Actuator can provide a base for many of these, extended as needed.

*   **`GET /actuator/health` or `GET /admin/health`**
    *   **Description:** Reports the overall health of the gateway, including connectivity to essential downstream systems (e.g., Kafka brokers if critical, DLQ status).
    *   **Response:** Standard Spring Boot health indicator format.
*   **`GET /actuator/info` or `GET /admin/info`**
    *   **Description:** Provides general information about the application (build version, etc.).
*   **`GET /actuator/metrics` or `GET /admin/metrics`**
    *   **Description:** Exposes operational metrics. Can point to or proxy the Micrometer metrics endpoint (e.g., `/actuator/prometheus`).
    *   **Response:** Prometheus format or JSON representation of available metrics.
*   **`GET /admin/config`**
    *   **Description:** Retrieves the current *effective* configuration of the gateway.
    *   **Query Params:** `?obfuscateSecrets=true` (default) to hide passwords, API keys.
    *   **Response:** YAML or JSON representation of the currently loaded configuration.
*   **`POST /admin/config/reload`**
    *   **Description:** Triggers a reload of the gateway's configuration from source (e.g., files).
    *   **Response:**
        *   `200 OK`: Reload successful.
        *   `202 Accepted`: Reload initiated, status can be checked via another endpoint or logs.
        *   `400 Bad Request`: Reload failed due to validation errors (response body includes details).
        *   `500 Internal Server Error`: Unexpected error during reload.
*   **Dynamic Rule Management (Advanced - may require persistent storage beyond config files if rules are frequently changed via API):**
    *   **Routing Rules:**
        *   `GET /admin/routes`: List current routing rules.
        *   `POST /admin/routes`: Add a new routing rule.
        *   `PUT /admin/routes/{routeId}`: Update an existing routing rule.
        *   `DELETE /admin/routes/{routeId}`: Delete a routing rule.
    *   **Rate Limit Profiles/Assignments:**
        *   `GET /admin/ratelimits`: List rate limit profiles and assignments.
        *   `POST /admin/ratelimits/profiles`: Add/update a rate limit profile.
        *   `POST /admin/ratelimits/assignments`: Add/update a rate limit assignment.
    *   **Consideration:** If these endpoints directly modify runtime configuration without persisting to the backing configuration files, those changes will be lost on restart unless a mechanism exists to write back to files or a central store. For simplicity, the initial focus might be on file-based changes reloaded via `/admin/config/reload`.

*   **Security for Admin API:**
    *   **Authentication:**
        *   **API Key:** Client sends an API key in a designated header (e.g., `X-Admin-API-Key`). The gateway validates the key against a configured list of (hashed) keys.
        *   **HTTP Basic Authentication:** Standard username/password.
        *   **OAuth2 Client Credentials:** For service-to-service communication if the admin client is automated.
        *   The choice (`security.adminApi.authentication` in YAML) determines the mechanism. Secrets must be managed securely (env vars, secrets store).
    *   **Authorization (Role-Based Access Control - RBAC):**
        *   Authenticated principals are assigned roles (e.g., `ADMIN`, `OPERATOR`, `VIEWER`).
        *   Endpoints are protected based on required roles.
            *   `ADMIN`: Full control (e.g., reload config, modify rules).
            *   `OPERATOR`: Operational tasks (e.g., trigger reload, view status).
            *   `VIEWER`: Read-only access (e.g., view config, health, metrics).
        *   Spring Security can be used to implement this.
    *   **Network Restrictions:** Admin endpoints should ideally be bound to a separate network interface or firewalled to be accessible only from trusted internal networks.
    *   **HTTPS:** Admin API must be served over HTTPS in production.

### 9.4. Configuration Validation

*   **When:**
    1.  **Startup:** The gateway validates the entire configuration upon application startup. If validation fails, the gateway should fail to start, logging detailed errors.
    2.  **Dynamic Reload:** Before applying a dynamically reloaded configuration, it is fully validated. If validation fails, the reload is aborted, the existing configuration remains active, and an error is logged/returned.
*   **Mechanisms:**
    1.  **Bean Validation (JSR 380):** Use annotations (`@NotNull`, `@Min`, `@Pattern`, custom validators) on Java classes that represent configuration structures (e.g., classes populated by Jackson from YAML). Spring Boot heavily uses this.
    2.  **Custom Validation Logic:** For complex cross-field validation or business rule checks within configuration (e.g., "does this Kafka topic referenced in a routing rule actually have a configured consumer?"), custom `Validator` components can be implemented.
    3.  **Schema Validation (for sub-parts):** While the main config is YAML, if parts are JSON (like JSON Schemas themselves), they are validated against their meta-schemas.
*   **Reporting:**
    *   **Startup:** Errors logged to the console and application logs, causing startup failure.
    *   **Dynamic Reload (API):** Error details returned in the HTTP response body of the reload endpoint.
    *   **Dynamic Reload (File Polling):** Errors logged to application logs. The gateway might enter a "degraded" state or continue with old config, clearly indicated in logs and health status.
    *   Error messages should be clear, pointing to the problematic configuration key and the reason for failure.

### 9.5. Usability and Documentation

*   **Clear Structure:** The hierarchical YAML structure itself, with well-named keys, contributes to usability.
*   **Comments in YAML:** Encourage extensive use of comments in the default configuration files to explain each section and parameter.
*   **Default Configuration File:** Provide a comprehensive `data-gateway.yaml` with all possible options commented out or set to sensible defaults, serving as a template.
*   **External Documentation:**
    *   A dedicated documentation page or Markdown file explaining each configuration section, parameter, possible values, and examples.
    *   Separate documentation for advanced configurations like transformation script writing, rate limit expressions, and routing conditions.
    *   Clear instructions on how to manage secrets and use environment variables.
*   **Validation Feedback:** Ensure validation error messages are user-friendly and guide the user to fix issues.

By implementing these configuration management strategies and administrative APIs, the Data Gateway can be made robust, flexible, and manageable throughout its lifecycle.
```
