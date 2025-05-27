package com.example.datagateway.formatnormalization.processor;

import com.example.datagateway.formatnormalization.config.ProtobufFormatProperties;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Processor for handling Protobuf data, including parsing and JSON conversions.
 */
@Component
@Slf4j
public class ProtobufProcessor {

    private final ProtobufFormatProperties protobufFormatProperties;
    private final JsonFormat.Printer jsonPrinter;
    private final JsonFormat.Parser jsonParser;

    /**
     * Constructs a ProtobufProcessor.
     *
     * @param protobufFormatProperties Configuration properties for Protobuf processing.
     */
    @Autowired
    public ProtobufProcessor(ProtobufFormatProperties protobufFormatProperties) {
        this.protobufFormatProperties = protobufFormatProperties;

        JsonFormat.Printer.Builder printerBuilder = JsonFormat.printer();
        if (protobufFormatProperties.getJsonPrinter().isPreservingProtoFieldNames()) {
            printerBuilder.preservingProtoFieldNames();
        }
        if (protobufFormatProperties.getJsonPrinter().isIncludingDefaultValueFields()) {
            printerBuilder.includingDefaultValueFields();
        }
        this.jsonPrinter = printerBuilder.build();

        JsonFormat.Parser.Builder parserBuilder = JsonFormat.parser();
        if (protobufFormatProperties.getJsonParser().isIgnoringUnknownFields()) {
            parserBuilder.ignoringUnknownFields();
        }
        this.jsonParser = parserBuilder.build();
    }

    /**
     * Parses a Protobuf message from byte array into a specific {@link Message} type.
     * The target class name is determined from configuration based on the sourceIdentifier.
     *
     * @param protoBytes       The byte array containing Protobuf data.
     * @param sourceIdentifier Identifier for the data source, used to look up the target Message class.
     * @return A specific {@link Message} instance.
     * @throws InvalidProtocolBufferException If parsing fails or the target class is not found/valid.
     */
    @SuppressWarnings("unchecked")
    public Message parse(byte[] protoBytes, String sourceIdentifier) throws InvalidProtocolBufferException {
        String className = protobufFormatProperties.getMessageClassNameForSource(sourceIdentifier);
        if (className == null) {
            log.warn("No Protobuf message type mapping found for sourceIdentifier: {}. Cannot parse to specific type.", sourceIdentifier);
            // Optionally, could attempt to parse as DynamicMessage if a Descriptor is available,
            // but that requires .desc file or more complex schema management.
            // For now, we'll indicate an issue if a specific type is expected but not mapped.
            throw new InvalidProtocolBufferException("Protobuf message type not configured for source: " + sourceIdentifier);
        }

        try {
            Class<?> clazz = Class.forName(className);
            if (!Message.class.isAssignableFrom(clazz)) {
                throw new InvalidProtocolBufferException("Configured class " + className + " is not a Protobuf Message type.");
            }
            Method method = clazz.getMethod("parseFrom", byte[].class);
            return (Message) method.invoke(null, (Object) protoBytes);
        } catch (ClassNotFoundException e) {
            log.error("Protobuf message class not found: {}", className, e);
            throw new InvalidProtocolBufferException("Protobuf message class not found: " + className + ". Error: " + e.getMessage());
        } catch (NoSuchMethodException e) {
            log.error("Protobuf message class {} is missing 'parseFrom(byte[])' method.", className, e);
            throw new InvalidProtocolBufferException("Protobuf message class " + className + " is invalid (missing parseFrom method). Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to parse Protobuf message for class {}: {}", className, e.getMessage(), e);
            if (e instanceof InvalidProtocolBufferException) {
                throw (InvalidProtocolBufferException) e;
            }
            throw new InvalidProtocolBufferException("Failed to parse Protobuf message: " + e.getMessage());
        }
    }

    /**
     * Converts a Protobuf {@link Message} to its JSON string representation.
     *
     * @param message The Protobuf message.
     * @return JSON string representation.
     * @throws InvalidProtocolBufferException If conversion fails.
     */
    public String toJson(Message message) throws InvalidProtocolBufferException {
        return jsonPrinter.print(message);
    }

    /**
     * Converts a JSON string to a Protobuf {@link Message}.
     *
     * @param jsonString The JSON string.
     * @param builder    A {@link Message.Builder} for the target Protobuf message type.
     * @return The parsed Protobuf {@link Message}.
     * @throws InvalidProtocolBufferException If conversion fails.
     */
    public Message fromJson(String jsonString, Message.Builder builder) throws InvalidProtocolBufferException {
        jsonParser.merge(jsonString, builder);
        return builder.build();
    }

    // Note: Parsing to DynamicMessage would require a Descriptor.
    // public DynamicMessage parseToDynamicMessage(byte[] protoBytes, Descriptors.Descriptor messageDescriptor) throws InvalidProtocolBufferException {
    //     return DynamicMessage.parseFrom(messageDescriptor, protoBytes);
    // }
}
