package com.example.datagateway.core.model;

/**
 * Represents the status of a data transformation process.
 * Typically part of {@link TransformedDataEvent}.
 */
public enum TransformationStatus {
    /**
     * Indicates that the transformation was completed successfully.
     */
    SUCCESS,
    /**
     * Indicates that the transformation failed.
     * Details of the failure should be available in {@link TransformedDataEvent#getTransformationErrors()}.
     */
    FAILURE
}
