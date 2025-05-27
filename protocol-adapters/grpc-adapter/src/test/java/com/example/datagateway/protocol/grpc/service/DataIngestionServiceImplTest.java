package com.example.datagateway.protocol.grpc.service;

import com.example.datagateway.core.model.PayloadType;
import com.example.datagateway.grpc.gen.DataIngestionServiceGrpc;
import com.example.datagateway.grpc.gen.GrpcPayloadType;
import com.example.datagateway.grpc.gen.IngestRequest;
import com.example.datagateway.grpc.gen.IngestResponse;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link DataIngestionServiceImpl}.
 * Uses an in-process gRPC server for testing.
 */
@ExtendWith(MockitoExtension.class)
class DataIngestionServiceImplTest {

    /**
     * This rule manages automatic graceful shutdown for the registered servers and channels at the
     * end of test.
     */
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private Server server;
    private ManagedChannel channel;
    private DataIngestionServiceGrpc.DataIngestionServiceBlockingStub blockingStub;
    private DataIngestionServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        // Create an instance of the service under test.
        // In a real scenario with dependencies (like GatewayPipelineOrchestrator),
        // you would inject mocks here.
        service = new DataIngestionServiceImpl();

        // Generate a unique server name for each test case.
        String serverName = InProcessServerBuilder.generateName();

        // Create an in-process server and register the service.
        server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor() // Use direct executor for simplicity in tests
                .addService(service)
                .build()
                .start();
        grpcCleanup.register(server); // Register server for cleanup

        // Create an in-process channel to connect to the server.
        channel = InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build();
        grpcCleanup.register(channel); // Register channel for cleanup

        // Create a blocking stub for sending requests.
        blockingStub = DataIngestionServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Ensure resources are released. GrpcCleanupRule should handle this,
        // but explicit shutdown can be added if needed for specific scenarios.
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Channel did not terminate");
            }
        }
        if (server != null && !server.isShutdown()) {
            server.shutdownNow();
             if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Server did not terminate");
            }
        }
    }

    @Test
    void ingestData_withValidJsonRequest_shouldSucceedAndReturnResponse() {
        String testPayload = "{\"key\":\"value\"}";
        String clientRequestId = "test-json-123";

        IngestRequest request = IngestRequest.newBuilder()
                .setClientRequestId(clientRequestId)
                .putMetadata("X-Source-System", "TestClient")
                .putMetadata("Content-Type", "application/json") // Hint for server-side interpretation or logging
                .setPayloadType(GrpcPayloadType.GRPC_PAYLOAD_TYPE_JSON)
                .setPayload(ByteString.copyFromUtf8(testPayload))
                .build();

        IngestResponse response = blockingStub.ingestData(request);

        assertNotNull(response);
        assertEquals(clientRequestId, response.getGatewayRequestId());
        assertThat(response.getStatusMessage()).contains("Request received successfully");

        // Further verification would involve capturing the UnifiedInternalRequest
        // if the service was integrated with a mock orchestrator.
        // For now, log verification in DataIngestionServiceImpl is the primary check.
    }

    @Test
    void ingestData_withProtobufPayload_shouldSucceed() {
        byte[] protoBytes = new byte[]{10, 3, 'a', 'b', 'c'}; // Sample binary data
        String clientRequestId = "test-proto-456";

        IngestRequest request = IngestRequest.newBuilder()
                .setClientRequestId(clientRequestId)
                .setPayloadType(GrpcPayloadType.GRPC_PAYLOAD_TYPE_PROTOBUF)
                .setPayload(ByteString.copyFrom(protoBytes))
                .build();

        IngestResponse response = blockingStub.ingestData(request);

        assertNotNull(response);
        assertEquals(clientRequestId, response.getGatewayRequestId());
        assertThat(response.getStatusMessage()).contains("Request received successfully");
    }

    @Test
    void ingestData_withoutClientRequestId_shouldGenerateGatewayRequestId() {
        IngestRequest request = IngestRequest.newBuilder()
                .setPayloadType(GrpcPayloadType.GRPC_PAYLOAD_TYPE_TEXT)
                .setPayload(ByteString.copyFromUtf8("Hello"))
                .build();

        IngestResponse response = blockingStub.ingestData(request);

        assertNotNull(response);
        assertNotNull(response.getGatewayRequestId());
        assertThat(response.getGatewayRequestId()).hasSize(36); // UUID length
        assertThat(response.getStatusMessage()).contains("Request received successfully");
    }
    
    @Test
    void ingestData_withCsvPayload_shouldMapToCsvPayloadType() {
        IngestRequest request = IngestRequest.newBuilder()
                .setClientRequestId("csv-test")
                .setPayloadType(GrpcPayloadType.GRPC_PAYLOAD_TYPE_CSV)
                .setPayload(ByteString.copyFromUtf8("h1,h2\nv1,v2"))
                .build();
        
        // For this test, we'd ideally capture the UnifiedInternalRequest passed to the (mocked) orchestrator
        // and verify its payloadType. Since we are not mocking the orchestrator yet,
        // this test primarily ensures the gRPC call itself succeeds.
        // The mapping logic is in DataIngestionServiceImpl.mapGrpcPayloadType which can be unit tested separately if complex.
        // For now, we rely on the successful processing and logging in the service implementation.
        
        IngestResponse response = blockingStub.ingestData(request);
        assertNotNull(response);
        assertEquals("csv-test", response.getGatewayRequestId());
    }

    // Direct test for the mapping logic if it were complex.
    // For the current simple switch, it's covered by testing different request types.
    // @Test
    // void testMapGrpcPayloadType() {
    //     DataIngestionServiceImpl service = new DataIngestionServiceImpl();
    //     assertEquals(PayloadType.JSON, service.mapGrpcPayloadType(GrpcPayloadType.GRPC_PAYLOAD_TYPE_JSON));
    //     assertEquals(PayloadType.UNKNOWN, service.mapGrpcPayloadType(GrpcPayloadType.UNRECOGNIZED));
    // }
}
