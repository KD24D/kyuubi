package com.example.datagateway.formatnormalization.processor;

import com.example.datagateway.formatnormalization.config.CsvParsingProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.hadoop.conf.Configuration; // Required by Parquet
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.DelegatingPositionOutputStream;
import org.apache.parquet.io.DelegatingSeekableInputStream;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;
import org.apache.parquet.io.SeekableInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Processor for handling CSV data, including parsing and conversion to Parquet.
 */
@Component
@Slf4j
public class CsvProcessor {

    private final CsvParsingProperties csvParsingProperties;
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    private final Map<String, Schema> avroSchemaCache = new ConcurrentHashMap<>();


    /**
     * Constructs a CsvProcessor.
     *
     * @param csvParsingProperties Configuration properties for CSV parsing and conversion.
     */
    @Autowired
    public CsvProcessor(CsvParsingProperties csvParsingProperties) {
        this.csvParsingProperties = csvParsingProperties;
    }

    /**
     * Parses a CSV payload from byte array into a list of CSVRecords.
     *
     * @param csvBytes         The byte array containing CSV data.
     * @param encoding         The character encoding of the byte array.
     * @param sourceIdentifier Identifier for the data source, used to look up specific CSV format settings.
     * @return A list of {@link CSVRecord} objects.
     * @throws IOException If parsing fails.
     */
    public List<CSVRecord> parse(byte[] csvBytes, String encoding, String sourceIdentifier) throws IOException {
        String csvString = new String(csvBytes, encoding != null ? encoding : StandardCharsets.UTF_8.name());
        Reader reader = new InputStreamReader(new ByteArrayInputStream(csvBytes), encoding != null ? encoding : StandardCharsets.UTF_8.name());

        CsvParsingProperties.SourceSpecificCsvFormat sourceConfig = csvParsingProperties.findConfigForSource(sourceIdentifier);
        CSVFormat.Builder formatBuilder;
        if (sourceConfig != null) {
            formatBuilder = sourceConfig.getFormat().toCsvFormatBuilder();
        } else {
            formatBuilder = csvParsingProperties.getDefaultFormat().toCsvFormatBuilder();
        }
        
        CSVFormat csvFormat = formatBuilder.build();

        try (CSVParser parser = new CSVParser(reader, csvFormat)) {
            return parser.getRecords();
        }
    }

    /**
     * Converts a list of CSVRecords to a Parquet formatted byte array.
     *
     * @param records          The list of {@link CSVRecord} objects.
     * @param sourceIdentifier Identifier for the data source, used to look up Avro schema path or CSV format.
     * @return Byte array containing Parquet data.
     * @throws IOException If conversion fails.
     */
    public byte[] convertToParquet(List<CSVRecord> records, String sourceIdentifier) throws IOException {
        if (records == null || records.isEmpty()) {
            return new byte[0];
        }

        CsvParsingProperties.SourceSpecificCsvFormat sourceConfig = csvParsingProperties.findConfigForSource(sourceIdentifier);
        Schema avroSchema = null;

        if (sourceConfig != null && sourceConfig.getAvroSchemaPath() != null && !sourceConfig.getAvroSchemaPath().isBlank()) {
            avroSchema = loadAvroSchema(sourceConfig.getAvroSchemaPath());
        } else {
            // Basic schema inference from headers if available, or from configured column names
            Map<String, Integer> headerMap = records.get(0).getParser().getHeaderMap();
            List<String> columnNames = null;
            if (headerMap != null && !headerMap.isEmpty()) {
                columnNames = new ArrayList<>(headerMap.keySet()); // Order might not be guaranteed, depends on CSVParser impl
            } else if (sourceConfig != null && sourceConfig.getFormat().getColumnNames() != null && !sourceConfig.getFormat().getColumnNames().isEmpty()) {
                columnNames = sourceConfig.getFormat().getColumnNames();
            } else if (csvParsingProperties.getDefaultFormat().getColumnNames() != null && !csvParsingProperties.getDefaultFormat().getColumnNames().isEmpty()){
                columnNames = csvParsingProperties.getDefaultFormat().getColumnNames();
            }

            if (columnNames != null && !columnNames.isEmpty()) {
                avroSchema = inferAvroSchemaFromHeaders(columnNames, records);
            } else {
                throw new IOException("Cannot convert CSV to Parquet without headers or a configured Avro schema for source: " + sourceIdentifier);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PositionOutputStream pos = new InMemoryPositionOutputStream(baos); // Custom OutputFile implementation
        
        Configuration conf = new Configuration(); // Hadoop config, can be minimal for local Parquet writing

        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(new InMemoryOutputFile(pos))
                .withSchema(avroSchema)
                .withConf(conf)
                .withCompressionCodec(CompressionCodecName.SNAPPY) // Or make configurable
                .build()) {

            for (CSVRecord csvRecord : records) {
                GenericRecord avroRecord = new GenericData.Record(avroSchema);
                for (Schema.Field field : avroSchema.getFields()) {
                    String csvValue = null;
                    if (csvRecord.isMapped(field.name())) { // Check if header/column name exists
                         csvValue = csvRecord.get(field.name());
                    } else if (field.pos() < csvRecord.size()) { // Fallback to position if not mapped by name
                        csvValue = csvRecord.get(field.pos());
                    }

                    if (csvValue != null) {
                        avroRecord.put(field.name(), convertCsvValueToSchemaType(csvValue, field.schema()));
                    } else if (field.schema().isNullable()) {
                        avroRecord.put(field.name(), null);
                    } else {
                        // Handle cases where field is not nullable and value is missing - error or default?
                        log.warn("Missing value for non-nullable Avro field '{}' at record {}. Skipping field or using default if any.", field.name(), csvRecord.getRecordNumber());
                        // For simplicity, we might skip or rely on Avro's default if defined in schema.
                        // Or throw new IOException("Missing value for non-nullable field: " + field.name());
                    }
                }
                writer.write(avroRecord);
            }
        }
        return baos.toByteArray();
    }

    private Schema loadAvroSchema(String schemaPath) throws IOException {
        if (avroSchemaCache.containsKey(schemaPath)) {
            return avroSchemaCache.get(schemaPath);
        }
        Resource schemaResource = resourceLoader.getResource(schemaPath);
        if (!schemaResource.exists()) {
            throw new IOException("Avro schema resource not found: " + schemaPath);
        }
        try (InputStream inputStream = schemaResource.getInputStream()) {
            Schema schema = new Schema.Parser().parse(inputStream);
            avroSchemaCache.put(schemaPath, schema);
            return schema;
        }
    }

    private Schema inferAvroSchemaFromHeaders(List<String> headers, List<CSVRecord> records) {
        // Basic inference: treat all fields as nullable strings for simplicity.
        // More sophisticated inference could sample data to guess types (int, long, double, boolean).
        List<Schema.Field> fields = new ArrayList<>();
        for (String header : headers) {
            // Sanitize header name for Avro field name rules (starts with [A-Za-z_], contains only [A-Za-z0-9_])
            String avroCompatibleHeader = header.replaceAll("[^A-Za-z0-9_]", "_");
            if (Character.isDigit(avroCompatibleHeader.charAt(0))) {
                avroCompatibleHeader = "_" + avroCompatibleHeader;
            }
            Schema fieldSchema = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.STRING));
            fields.add(new Schema.Field(avroCompatibleHeader, fieldSchema, null, Schema.Field.NULL_DEFAULT_VALUE));
        }
        Schema schema = Schema.createRecord("CsvRecord", "Inferred CSV schema", "com.example.datagateway.avro", false);
        schema.setFields(fields);
        return schema;
    }
    
    private Object convertCsvValueToSchemaType(String csvValue, Schema fieldSchema) {
        // This is a simplified converter. A real implementation would handle more types and potential errors.
        Schema.Type type = fieldSchema.isUnion() ?
                fieldSchema.getTypes().stream().filter(t -> t.getType() != Schema.Type.NULL).findFirst().orElseThrow().getType() :
                fieldSchema.getType();

        if (csvValue == null || csvValue.isEmpty() || "null".equalsIgnoreCase(csvValue.trim())) {
            if (fieldSchema.isNullable()) return null;
            // Handle error or default for non-nullable empty value if necessary
        }

        try {
            switch (type) {
                case STRING:  return csvValue;
                case INT:     return Integer.parseInt(csvValue);
                case LONG:    return Long.parseLong(csvValue);
                case FLOAT:   return Float.parseFloat(csvValue);
                case DOUBLE:  return Double.parseDouble(csvValue);
                case BOOLEAN: return Boolean.parseBoolean(csvValue);
                // Add other types as needed (BYTES, ENUM, FIXED, etc.)
                default:
                    log.warn("Unsupported Avro type '{}' for CSV conversion. Treating as String.", type);
                    return csvValue;
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse CSV value '{}' to Avro type '{}'. Returning as String or null. Error: {}", csvValue, type, e.getMessage());
            // Fallback for parsing errors, or throw an exception if strictness is required
            if (fieldSchema.isNullable()) return null;
            return csvValue; // Or throw new RuntimeException("Cannot convert '" + csvValue + "' to " + type, e);
        }
    }

    // Inner classes for Parquet InMemory OutputFile (required by ParquetWriter builder)
    private static class InMemoryOutputFile implements OutputFile {
        private final PositionOutputStream outputStream;
        InMemoryOutputFile(PositionOutputStream outputStream) {
            this.outputStream = outputStream;
        }
        @Override
        public PositionOutputStream create(long blockSizeHint) {
            return outputStream;
        }
        @Override
        public PositionOutputStream createOrOverwrite(long blockSizeHint) {
            return outputStream;
        }
        @Override
        public boolean supportsBlockSize() {
            return false;
        }
        @Override
        public long defaultBlockSize() {
            return 0;
        }
    }

    private static class InMemoryPositionOutputStream extends DelegatingPositionOutputStream {
        private long position = 0;
        private final ByteArrayOutputStream byteArrayOutputStream;

        InMemoryPositionOutputStream(ByteArrayOutputStream byteArrayOutputStream) {
            super(byteArrayOutputStream); // Delegate to the underlying BAOS
            this.byteArrayOutputStream = byteArrayOutputStream;
        }

        @Override
        public long getPos() {
            return position;
        }

        @Override
        public void write(int b) throws IOException {
            super.write(b);
            position++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            super.write(b);
            position += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            super.write(b, off, len);
            position += len;
        }
    }
}
