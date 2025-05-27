package com.example.datagateway.protocol.grpc.service;

import com.example.datagateway.core.model.PayloadType;
import com.example.datagateway.core.model.Protocol;
import com.example.datagateway.core.model.SourceInfo;
import com.example.datagateway.core.model.UnifiedInternalRequest;
import com.example.datagateway.grpc.gen.DataIngestionServiceGrpc;
import com.example.datagateway.grpc.gen.GrpcPayloadType;
import com.example.datagateway.grpc.gen.IngestRequest;
import com.example.datagateway.grpc.gen.IngestResponse;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the gRPC DataIngestionService defined in ingestion.proto.
 * This service handles incoming gRPC requests, converts them to a
 * {@link UnifiedInternalRequest}, and (as a placeholder) logs this internal request.
 * In a full implementation, it would forward the request to the Core Processing Pipeline.
 */
@Slf4j
public class DataIngestionServiceImpl extends DataIngestionServiceGrpc.DataIngestionServiceImplBase {

    // In a real application, this would be injected and used to pass the UIR to the pipeline.
    // private final GatewayPipelineOrchestrator pipelineOrchestrator;
    // public DataIngestionServiceImpl(GatewayPipelineOrchestrator pipelineOrchestrator) {
    //     this.pipelineOrchestrator = pipelineOrchestrator;
    // }


    /**
     * Handles a single data ingestion request.
     *
     * @param grpcRequest      The incoming {@link IngestRequest} from the gRPC client.
     * @param responseObserver A {@link StreamObserver} to send the {@link IngestResponse} back to the client.
     */
    @Override
    public void ingestData(IngestRequest grpcRequest, StreamObserver<IngestResponse> responseObserver) {
        try {
            long receivedTimestamp = Instant.now().toEpochMilli();

            // 1. Extract or generate requestId
            String clientRequestId = grpcRequest.getClientRequestId();
            String gatewayRequestId = (clientRequestId == null || clientRequestId.isEmpty())
                    ? UUID.randomUUID().toString()
                    : clientRequestId;

            // 2. Extract client information (simplified for now)
            // For a real implementation, consider io.grpc.ServerInterceptors and Context
            // to capture client IP, etc. For example, using GrpcServerConstants.REMOTE_ADDR_KEY:
            // String clientIp = Context.current().Value(GrpcServerConstants.REMOTE_ADDR_KEY).toString();
            // For now, using a placeholder.
            String sourceAddress = "grpc-client:" + Context.current().toString(); // Placeholder


            // 3. Populate SourceInfo
            SourceInfo sourceInfo = SourceInfo.builder()
                    .protocol(Protocol.GRPC)
                    .sourceAddress(sourceAddress) // Placeholder, real IP needs interceptor
                    .requestTarget(DataIngestionServiceGrpc.SERVICE_NAME + "/IngestData") // Service/method
                    .build();

            // 4. Convert payload and determine PayloadType
            byte[] payloadBytes = grpcRequest.getPayload().toByteArray();
            PayloadType corePayloadType = mapGrpcPayloadType(grpcRequest.getPayloadType());

            // 5. Populate metadata
            Map<String, String> metadata = grpcRequest.getMetadataMap(); // Already a Map<String, String>

            // 6. Create UnifiedInternalRequest
            UnifiedInternalRequest unifiedRequest = UnifiedInternalRequest.builder()
                    .requestId(gatewayRequestId)
                    .payload(payloadBytes)
                    .payloadType(corePayloadType)
                    .sourceInfo(sourceInfo)
                    .metadata(metadata)
                    .receivedTimestamp(receivedTimestamp)
                    // OriginalEncoding is less relevant for gRPC if PayloadType specifies it (e.g. JSON_TEXT implies UTF-8 usually)
                    // or if it's binary. Could be added to gRPC metadata if needed.
                    .originalEncoding(corePayloadType == PayloadType.JSON || corePayloadType == PayloadType.XML || corePayloadType == PayloadType.TEXT || corePayloadType == PayloadType.CSV ? "UTF-8" : null)
                    .build();

            // 7. Placeholder for Orchestrator Integration
            log.info("gRPC Adapter: Created UnifiedInternalRequest: RequestId={}, Source={}, PayloadType={}, Size={}",
                    unifiedRequest.getRequestId(),
                    unifiedRequest.getSourceInfo().getRequestTarget(),
                    unifiedRequest.getPayloadType(),
                    payloadBytes.length);
            log.debug("gRPC Adapter: UnifiedInternalRequest details: {}", unifiedRequest);

            // This would be:
            // pipelineOrchestrator.processHttpRequest(Mono.just(unifiedRequest)) or processRequest
            //     .subscribe(
            //         responseEntity -> { /* map to gRPC response */ },
            //         error -> { /* map to gRPC error */ }
            //     );

            // 8. Send IngestResponse
            IngestResponse grpcResponse = IngestResponse.newBuilder()
                    .setGatewayRequestId(gatewayRequestId)
                    .setStatusMessage("Request received successfully by gRPC adapter.")
                    .build();
            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC Adapter: Error processing IngestRequest: {}", e.getMessage(), e);
            Status status = Status.INTERNAL.withDescription("Error processing request: " + e.getMessage()).withCause(e);
            responseObserver.onError(status.asRuntimeException());
        }
    }

    /**
     * Maps the gRPC {@link GrpcPayloadType} enum to the gateway's core {@link PayloadType} enum.
     *
     * @param grpcPayloadType The {@link GrpcPayloadType} from the gRPC request.
     * @return The corresponding core {@link PayloadType}.
     */
    private PayloadType mapGrpcPayloadType(GrpcPayloadType grpcPayloadType) {
        return switch (grpcPayloadType) {
            case GRPC_PAYLOAD_TYPE_JSON -> PayloadType.JSON;
            case GRPC_PAYLOAD_TYPE_PROTOBUF -> PayloadType.PROTOBUF;
            case GRPC_PAYLOAD_TYPE_XML -> PayloadType.XML;
            case GRPC_PAYLOAD_TYPE_CSV -> PayloadType.CSV;
            case GRPC_PAYLOAD_TYPE_BINARY -> PayloadType.BINARY;
            case GRPC_PAYLOAD_TYPE_TEXT -> PayloadType.TEXT;
            default -> PayloadType.UNKNOWN; // Includes GRPC_PAYLOAD_TYPE_UNKNOWN and UNRECOGNIZED
        };
    }
}
