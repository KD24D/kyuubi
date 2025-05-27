package com.example.gateway.core.model;

public enum PayloadType {
    JSON,
    PROTOBUF,
    XML, // Included as per DESIGN.md example, though not primary focus initially
    CSV,
    BINARY,
    TEXT,
    PARQUET, // For post-conversion representation
    UNKNOWN
}
