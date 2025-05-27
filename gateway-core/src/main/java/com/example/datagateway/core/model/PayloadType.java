package com.example.datagateway.core.model;

/**
 * Represents the type or format of a payload within the Data Gateway.
 */
public enum PayloadType {
    /**
     * JavaScript Object Notation.
     */
    JSON,
    /**
     * Protocol Buffers.
     */
    PROTOBUF,
    /**
     * Extensible Markup Language.
     */
    XML,
    /**
     * Comma-Separated Values.
     */
    CSV,
    /**
     * Raw binary data with no specific higher-level format known or applicable.
     */
    BINARY,
    /**
     * Plain text content.
     */
    TEXT,
    /**
     * Apache Parquet columnar storage file format.
     */
    PARQUET,
    /**
     * The type of the payload is unknown or could not be determined.
     */
    UNKNOWN
}
