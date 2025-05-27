package com.example.gateway.formatnormalization.csv;

import com.example.gateway.core.model.NormalizedDataEvent;
import com.example.gateway.core.model.PayloadType;
import com.example.gateway.core.model.UnifiedInternalRequest;
import com.example.gateway.core.model.ValidationResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CsvProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(CsvProcessorService.class);

    public CsvProcessorService() {
        // Constructor
    }

    public NormalizedDataEvent processCsv(UnifiedInternalRequest uir) {
        NormalizedDataEvent event = new NormalizedDataEvent();
        event.setRequestId(uir.getRequestId());
        event.setSourceInfo(uir.getSourceInfo());
        event.setOriginalPayloadType(PayloadType.CSV);
        event.setProcessingMetadata(uir.getMetadata()); // Carry over metadata

        ValidationResult validationResult = new ValidationResult(true, new ArrayList<>());
        List<Map<String, String>> parsedPayload = null; // Store as List of Maps (headers to values)

        try {
            if (!(uir.getPayload() instanceof byte[]) && !(uir.getPayload() instanceof String)) {
                throw new IllegalArgumentException("Unsupported CSV payload type: " +
                                                   (uir.getPayload() == null ? "null" : uir.getPayload().getClass().getName()) +
                                                   ". Expected byte[] or String.");
            }

            String csvString;
            if (uir.getPayload() instanceof byte[]) {
                String encoding = uir.getOriginalEncoding() != null ? uir.getOriginalEncoding() : StandardCharsets.UTF_8.name();
                csvString = new String((byte[]) uir.getPayload(), encoding);
            } else {
                csvString = (String) uir.getPayload();
            }

            // Basic CSVFormat - assuming header auto-detection and comma delimiter.
            // This should be configurable per source in a real system.
            // Example: uir.getMetadata().get("csv.delimiter") or from a config map.
            char delimiter = uir.getMetadata() != null && uir.getMetadata().containsKey("csv.delimiter") ?
                             uir.getMetadata().get("csv.delimiter").charAt(0) : ',';
            boolean headerPresent = uir.getMetadata() == null || !"false".equalsIgnoreCase(uir.getMetadata().get("csv.headerPresent"));


            CSVFormat.Builder formatBuilder = CSVFormat.DEFAULT.builder()
                    .setDelimiter(delimiter)
                    .setTrim(true)
                    .setIgnoreEmptyLines(true);

            if (headerPresent) {
                formatBuilder.setHeader().setSkipHeaderRecord(true);
            }
            // Add other configurations from metadata if available:
            // e.g., .setQuote(uir.getMetadata().get("csv.quoteChar").charAt(0))

            CSVFormat csvFormat = formatBuilder.build();

            try (StringReader reader = new StringReader(csvString);
                 CSVParser parser = new CSVParser(reader, csvFormat)) {

                // Convert CSVRecords to List of Maps for a more generic "parsedPayload"
                // If no header is present, maps will use indexed keys "0", "1", ...
                // If header is present, maps will use header names as keys.
                parsedPayload = parser.getRecords().stream()
                        .map(CSVRecord::toMap) // Converts each record to a Map<String, String>
                        .collect(Collectors.toList());

                event.setParsedPayload(parsedPayload);
                event.setProcessedPayloadType(PayloadType.CSV); // Still CSV data, but parsed into a structure

                logger.debug("CSV payload parsed successfully for request {}. Records found: {}", uir.getRequestId(), parsedPayload.size());
                // Basic validation: Check if any records were parsed if data was expected.
                // More complex CSV validation (e.g., column counts, data types per column) would go here
                // or in a subsequent generic validation step.
                if (csvString.length() > 0 && parsedPayload.isEmpty() && !parser.getHeaderNames().isEmpty() && headerPresent) {
                    // This can happen if only a header row exists. Not necessarily an error.
                    logger.debug("CSV for request {} contained only a header row or was empty after header.", uir.getRequestId());
                } else if (csvString.length() > 0 && parsedPayload.isEmpty() && !headerPresent) {
                     logger.warn("CSV for request {} was not empty but resulted in zero records parsed (and no header defined).", uir.getRequestId());
                     // This could be an issue depending on expectations.
                }

            }

        } catch (IOException e) {
            logger.error("IOException processing CSV payload for request {}: {}", uir.getRequestId(), e.getMessage());
            validationResult.setValid(false);
            validationResult.getValidationErrors().add("Error reading CSV payload: " + e.getMessage());
        } catch (IllegalArgumentException e) { // Catches our own and potentially from CSVParser
            logger.error("Illegal argument for CSV processing, request {}: {}", uir.getRequestId(), e.getMessage());
            validationResult.setValid(false);
            validationResult.getValidationErrors().add(e.getMessage());
        } catch (Exception e) { // Catch other unexpected errors
            logger.error("Unexpected error during CSV processing for request {}: {}", uir.getRequestId(), e.getMessage(), e);
            validationResult.setValid(false);
            validationResult.getValidationErrors().add("Unexpected error: " + e.getMessage());
        }

        event.setValidationResult(validationResult);
        if (!validationResult.isValid() && parsedPayload == null) {
             event.setParsedPayload(null);
        }
        return event;
    }
}
