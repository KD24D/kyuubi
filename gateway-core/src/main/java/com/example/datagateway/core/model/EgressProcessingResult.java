package com.example.datagateway.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Represents the result of an egress (dispatch) operation.
 * This is the output of the Egress/Dispatch stage in the pipeline.
 */
@Value
@Builder
public class EgressProcessingResult {
    /**
     * The unique request ID, propagated from the {@link RoutedEvent}.
     */
    String requestId;

    /**
     * The intended destination for which this result is relevant.
     * Propagated from {@link RoutedEvent#getDestination()}.
     */
    RouteDestination destination;

    /**
     * The status of the egress operation (e.g., SUCCESS, FAILURE, RETRYABLE_FAILURE).
     */
    EgressStatus status;

    /**
     * A descriptive reason for the failure, if {@code status} is
     * {@link EgressStatus#FAILURE} or {@link EgressStatus#RETRYABLE_FAILURE}.
     * This field may be null if the operation was successful.
     */
    String failureReason;

    /**
     * The number of attempts made to dispatch the event to the destination.
     * This is particularly relevant if retry mechanisms are in place.
     */
    int attemptCount;

    /**
     * Metadata related to the response from the downstream system, if applicable.
     * For example, for an HTTP egress operation, this could include HTTP response headers.
     * This field may be null or empty if no such metadata is available or relevant.
     */
    Map<String, String> responseMetadata;
}
