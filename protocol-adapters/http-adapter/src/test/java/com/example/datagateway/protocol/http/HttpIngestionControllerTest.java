package com.example.datagateway.protocol.http;

import com.example.datagateway.core.model.PayloadType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WebFluxTest(HttpIngestionController.class)
class HttpIngestionControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    // In DESIGN.md, the controller takes a path from @Value.
    // We need to provide this value for the test context.
    private static final String TEST_INGESTION_PATH = "/test-ingest";

    @TestConfiguration
    static class TestConfig {
        @Bean
        public HttpIngestionController httpIngestionController() {
            return new HttpIngestionController(TEST_INGESTION_PATH);
        }
    }


    @BeforeEach
    void setUp() {
        // Ensure the base URI matches how WebFluxTest sets it up or override if needed.
        // webTestClient = webTestClient.mutate().baseUrl(TEST_INGESTION_PATH).build();
    }

    @Test
    void handlePostRequest_withJsonPayload_shouldSucceed() {
        String sourceId = "testSourceJson";
        String requestJson = "{\"key\":\"value\"}";
        String testRequestId = "test-json-request-id";

        EntityExchangeResult<String> result = webTestClient.post()
                .uri(TEST_INGESTION_PATH + "/" + sourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-ID", testRequestId)
                .header("X-Custom-Header", "CustomValue")
                .body(BodyInserters.fromValue(requestJson))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .returnResult();

        assertThat(result.getResponseBody()).contains("Request received by HTTP adapter, UIR created: " + testRequestId);

        // Further assertions would require mocking the orchestrator and capturing UnifiedInternalRequest
        // For now, we rely on the controller's log output for verification of UIR creation.
        // In a real scenario, you'd capture the argument to the mocked orchestrator.
    }

    @Test
    void handlePostRequest_withProtobufPayload_shouldSucceed() {
        String sourceId = "testSourceProtobuf";
        byte[] requestBody = new byte[]{10, 3, 97, 98, 99}; // Sample protobuf-like bytes
        String testRequestId = "test-proto-request-id";

        EntityExchangeResult<String> result = webTestClient.post()
                .uri(TEST_INGESTION_PATH + "/" + sourceId)
                .contentType(MediaType.valueOf("application/x-protobuf"))
                .header("X-Request-ID", testRequestId)
                .body(BodyInserters.fromValue(requestBody))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .returnResult();

        assertThat(result.getResponseBody()).contains("Request received by HTTP adapter, UIR created: " + testRequestId);
    }

    @Test
    void handlePostRequest_withCsvPayload_shouldSucceed() {
        String sourceId = "testSourceCsv";
        String requestCsv = "col1,col2\nval1,val2";

        EntityExchangeResult<String> result = webTestClient.post()
                .uri(TEST_INGESTION_PATH + "/" + sourceId)
                .contentType(MediaType.valueOf("text/csv"))
                .body(BodyInserters.fromValue(requestCsv))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .returnResult();

        assertNotNull(result.getResponseBody());
        assertThat(result.getResponseBody()).contains("Request received by HTTP adapter, UIR created: "); // UUID is random
    }
    
    @Test
    void handlePostRequest_withXmlPayload_shouldSucceed() {
        String sourceId = "testSourceXml";
        String requestXml = "<root><element>value</element></root>";

        EntityExchangeResult<String> result = webTestClient.post()
                .uri(TEST_INGESTION_PATH + "/" + sourceId)
                .contentType(MediaType.APPLICATION_XML)
                .body(BodyInserters.fromValue(requestXml))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .returnResult();
        
        assertNotNull(result.getResponseBody());
        assertThat(result.getResponseBody()).contains("Request received by HTTP adapter, UIR created: ");
    }

    @Test
    void handlePostRequest_withEmptyPayload_shouldSucceed() {
        String sourceId = "testSourceEmpty";

        EntityExchangeResult<String> result = webTestClient.post()
                .uri(TEST_INGESTION_PATH + "/" + sourceId)
                .contentType(MediaType.TEXT_PLAIN) // Content-Type is still important
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .returnResult();
        
        assertNotNull(result.getResponseBody());
        assertThat(result.getResponseBody()).contains("Empty body request received by HTTP adapter, UIR created: ");
    }

    @Test
    void handlePostRequest_withoutRequestIdHeader_shouldGenerateOne() {
        String sourceId = "testSourceNoReqId";
        String requestJson = "{\"key\":\"value\"}";

        EntityExchangeResult<String> result = webTestClient.post()
                .uri(TEST_INGESTION_PATH + "/" + sourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(requestJson))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .returnResult();

        // Check that a UUID was generated (length of UUID string is 36)
        // Example response: "Request received by HTTP adapter, UIR created: dab40090-2688-4208-8214-750c55e20918"
        String responseBody = result.getResponseBody();
        assertNotNull(responseBody);
        String createdRequestId = responseBody.substring(responseBody.lastIndexOf(": ") + 2);
        assertThat(createdRequestId).hasSize(36); // UUID length
    }
}
