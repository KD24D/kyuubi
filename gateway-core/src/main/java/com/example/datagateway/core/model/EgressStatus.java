package com.example.datagateway.core.model;

/**
 * Represents the status of an egress (dispatch) operation.
 * Typically part of {@link EgressProcessingResult}.
 */
public enum EgressStatus {
    /**
     * The egress operation was completed successfully.
     */
    SUCCESS,
    /**
     * The egress operation failed with a non-retryable error.
     */
    FAILURE,
    /**
     * The egress operation failed with an error that might be resolved by retrying.
     */
    RETRYABLE_FAILURE
}
