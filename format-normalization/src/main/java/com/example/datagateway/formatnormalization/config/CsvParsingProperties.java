package com.example.datagateway.formatnormalization.config;

import lombok.Data;
import org.apache.commons.csv.CSVFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for CSV parsing.
 * Allows defining global defaults and source-specific CSV format configurations.
 * <p>
 * Example YAML:
 * <pre>
 * gateway:
 *   format-normalization:
 *     csv:
 *       default-format:
 *         delimiter: ","
 *         headerPresent: true
 *         skipHeaderRecord: true
 *         # quoteChar: "\""
 *         # escapeChar: "\\"
 *         # nullString: "NULL"
 *         # recordSeparator: "\n"
 *         # ignoreEmptyLines: true
 *       source-specific-formats:
 *         - sourceIdentifierPattern: "kafka://product-feed-topic"
 *           format:
 *             delimiter: ";"
 *             headerPresent: true
 *             # Define other CSVFormat properties
 *           # Optional Avro schema for Parquet conversion for this source
 *           # avroSchemaPath: "classpath:schemas/avro/product-feed.avsc"
 *         - sourceIdentifierPattern: "/ingest/legacy-csv/.*" # Regex pattern
 *           format:
 *             delimiter: "\t"
 *             headerPresent: false
 *             # Explicit column names if no header, useful for Avro/Parquet schema
 *             # columnNames: ["id", "name", "value"]
 * </pre>
 */
@ConfigurationProperties(prefix = "gateway.format-normalization.csv")
@Component
@Validated
@Data
public class CsvParsingProperties {

    /**
     * Default CSV format settings to be used if no source-specific format matches.
     */
    @NotNull
    private CsvFormatConfig defaultFormat = new CsvFormatConfig();

    /**
     * A list of source-specific CSV format configurations.
     * These are evaluated in order, and the first matching {@code sourceIdentifierPattern} is used.
     */
    private List<SourceSpecificCsvFormat> sourceSpecificFormats = new ArrayList<>();

    @Data
    public static class CsvFormatConfig {
        private char delimiter = ',';
        private boolean headerPresent = true;
        private boolean skipHeaderRecord = true; // Relevant only if headerPresent is true
        private Character quoteChar = '"';
        private Character escapeChar;
        private String nullString;
        private String recordSeparator; // e.g., "\n", "\r\n"
        private boolean ignoreEmptyLines = true;
        private boolean ignoreHeaderCase = false;
        private boolean trim = true;
        private String commentMarker; // e.g. '#'
        // If headerPresent is false, columnNames can be used to define schema for Parquet/Avro
        private List<String> columnNames;

        /**
         * Builds an Apache Commons CSV {@link CSVFormat} object from this configuration.
         *
         * @return Configured {@link CSVFormat}.
         */
        public CSVFormat.Builder toCsvFormatBuilder() {
            CSVFormat.Builder builder = CSVFormat.DEFAULT.builder()
                    .setDelimiter(delimiter)
                    .setIgnoreEmptyLines(ignoreEmptyLines)
                    .setTrim(trim);

            if (headerPresent) {
                builder.setHeader(); // Assumes first record is header
                if (skipHeaderRecord) {
                    builder.setSkipHeaderRecord(true);
                }
                if (ignoreHeaderCase) {
                    builder.setIgnoreHeaderCase(true);
                }
            } else if (columnNames != null && !columnNames.isEmpty()) {
                builder.setHeader(columnNames.toArray(new String[0]));
                builder.setSkipHeaderRecord(false); // Explicitly no header record to skip
            }


            if (quoteChar != null) {
                builder.setQuote(quoteChar);
            }
            if (escapeChar != null) {
                builder.setEscape(escapeChar);
            }
            if (nullString != null && !nullString.isEmpty()) {
                builder.setNullString(nullString);
            }
            if (recordSeparator != null && !recordSeparator.isEmpty()) {
                builder.setRecordSeparator(recordSeparator);
            }
            if (commentMarker != null && !commentMarker.isEmpty()) {
                builder.setCommentMarker(commentMarker.charAt(0));
            }

            return builder;
        }
    }

    @Data
    public static class SourceSpecificCsvFormat {
        /**
         * A regex pattern to match against a source identifier (e.g., Kafka topic, HTTP path).
         */
        @NotBlank
        private String sourceIdentifierPattern;

        /**
         * CSV format configuration for sources matching the pattern.
         */
        @NotNull
        private CsvFormatConfig format = new CsvFormatConfig();

        /**
         * Optional path to an Avro schema file (.avsc) to be used when converting
         * CSV data from this source to Parquet.
         * If not provided, schema inference might be attempted (basic) or a default schema used.
         * Example: "classpath:schemas/avro/my-data.avsc"
         */
        private String avroSchemaPath;
    }

    /**
     * Finds the appropriate CSVFormatConfig for a given source identifier.
     *
     * @param sourceIdentifier The identifier of the data source (e.g., topic name, HTTP path).
     * @return The matching {@link SourceSpecificCsvFormat} if found, otherwise null.
     */
    public SourceSpecificCsvFormat findConfigForSource(String sourceIdentifier) {
        if (sourceIdentifier == null || sourceIdentifier.isEmpty()) {
            return null;
        }
        for (SourceSpecificCsvFormat config : sourceSpecificFormats) {
            if (Pattern.matches(config.getSourceIdentifierPattern(), sourceIdentifier)) {
                return config;
            }
        }
        return null;
    }
}
