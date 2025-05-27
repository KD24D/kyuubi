package com.example.datagateway.formatnormalization.processor;

import com.example.datagateway.core.model.ValidationResult;
import com.example.datagateway.formatnormalization.config.JsonValidationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonProcessorTest {

    private JsonProcessor jsonProcessor;
    private ObjectMapper objectMapper;

    @Mock
    private JsonValidationProperties mockJsonValidationProperties;

    @Mock
    private ResourceLoader mockResourceLoader; // Mock ResourceLoader for schema loading

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper(); // Real ObjectMapper
        // Configure mock properties
        lenient().when(mockJsonValidationProperties.getDefaultEncoding()).thenReturn("UTF-8");
        lenient().when(mockJsonValidationProperties.isSchemaValidationEnabled()).thenReturn(true);
        lenient().when(mockJsonValidationProperties.isCacheSchemas()).thenReturn(true); // Enable caching for test coverage

        jsonProcessor = new JsonProcessor(objectMapper, mockJsonValidationProperties);
        // Inject mock ResourceLoader into jsonProcessor for controlled schema loading
        // This uses reflection, which is common in tests for non-public fields.
        // Alternatively, make ResourceLoader injectable via constructor in JsonProcessor.
        org.springframework.test.util.ReflectionTestUtils.setField(jsonProcessor, "resourceLoader", mockResourceLoader);

    }

    @Test
    void parse_byteArray_validJson_shouldReturnMap() throws IOException {
        String jsonString = "{\"name\":\"test\", \"value\":123}";
        byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);

        Map<String, Object> result = jsonProcessor.parse(jsonBytes, "UTF-8");

        assertThat(result).containsEntry("name", "test").containsEntry("value", 123);
    }

    @Test
    void parse_string_validJson_shouldReturnMap() throws IOException {
        String jsonString = "{\"name\":\"test\", \"value\":123}";
        Map<String, Object> result = jsonProcessor.parse(jsonString);
        assertThat(result).containsEntry("name", "test").containsEntry("value", 123);
    }

    @Test
    void parseToJsonNode_byteArray_validJson_shouldReturnJsonNode() throws IOException {
        String jsonString = "{\"name\":\"test\", \"value\":123}";
        byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);

        JsonNode result = jsonProcessor.parseToJsonNode(jsonBytes, "UTF-8");

        assertThat(result.get("name").asText()).isEqualTo("test");
        assertThat(result.get("value").asInt()).isEqualTo(123);
    }

    @Test
    void parse_invalidJson_shouldThrowIOException() {
        String invalidJsonString = "{\"name\":\"test\", value:123}"; // Invalid JSON: value not quoted
        byte[] jsonBytes = invalidJsonString.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> jsonProcessor.parse(jsonBytes, "UTF-8"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void validate_validJsonAgainstSchema_shouldReturnValid() throws IOException {
        String jsonData = "{\"id\": 1, \"name\": \"ProductA\", \"price\": 10.99}";
        String schemaJson = "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"id\": {\"type\": \"integer\"},\n" +
                "    \"name\": {\"type\": \"string\"},\n" +
                "    \"price\": {\"type\": \"number\"}\n" +
                "  },\n" +
                "  \"required\": [\"id\", \"name\", \"price\"]\n" +
                "}";
        JsonNode jsonNode = objectMapper.readTree(jsonData);
        String schemaLocation = "classpath:schemas/test-schema.json";

        Resource mockSchemaResource = new ByteArrayResource(schemaJson.getBytes(StandardCharsets.UTF_8));
        when(mockResourceLoader.getResource(schemaLocation)).thenReturn(mockSchemaResource);
        when(mockJsonValidationProperties.getSchemaMappings()).thenReturn(Collections.singletonMap("source1", schemaLocation));


        ValidationResult result = jsonProcessor.validate(jsonNode, "source1");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getValidationErrors()).isEmpty();
    }

    @Test
    void validate_invalidJsonAgainstSchema_shouldReturnInvalidWithErrors() throws IOException {
        String jsonData = "{\"id\": \"not-an-integer\", \"name\": \"ProductA\"}"; // Missing price, wrong id type
        String schemaJson = "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"id\": {\"type\": \"integer\"},\n" +
                "    \"name\": {\"type\": \"string\"},\n" +
                "    \"price\": {\"type\": \"number\"}\n" +
                "  },\n" +
                "  \"required\": [\"id\", \"name\", \"price\"]\n" +
                "}";
        JsonNode jsonNode = objectMapper.readTree(jsonData);
        String schemaLocation = "classpath:schemas/test-schema.json";

        Resource mockSchemaResource = new ByteArrayResource(schemaJson.getBytes(StandardCharsets.UTF_8));
        when(mockResourceLoader.getResource(schemaLocation)).thenReturn(mockSchemaResource);
        when(mockJsonValidationProperties.getSchemaMappings()).thenReturn(Collections.singletonMap("source1", schemaLocation));

        ValidationResult result = jsonProcessor.validate(jsonNode, "source1");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getValidationErrors()).hasSize(2); // Expecting 2 errors: type mismatch for id, missing price
        assertThat(result.getValidationErrors().get(0)).contains("id: string found, integer expected");
        assertThat(result.getValidationErrors().get(1)).contains("price: is missing but it is required");

    }

    @Test
    void validate_whenSchemaValidationDisabled_shouldReturnValid() throws IOException {
        String jsonData = "{\"id\": \"not-an-integer\"}";
        JsonNode jsonNode = objectMapper.readTree(jsonData);
        when(mockJsonValidationProperties.isSchemaValidationEnabled()).thenReturn(false); // Disable validation

        ValidationResult result = jsonProcessor.validate(jsonNode, "source1");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getValidationErrors()).isEmpty();
    }

    @Test
    void validate_whenNoSchemaMappingFound_shouldReturnValid() throws IOException {
        String jsonData = "{\"id\": 1}";
        JsonNode jsonNode = objectMapper.readTree(jsonData);
        when(mockJsonValidationProperties.getSchemaMappings()).thenReturn(Collections.emptyMap()); // No mapping

        ValidationResult result = jsonProcessor.validate(jsonNode, "source-without-schema");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getValidationErrors()).isEmpty();
    }

    @Test
    void validate_whenSchemaNotFound_shouldReturnInvalid() throws IOException {
        String jsonData = "{\"id\": 1}";
        JsonNode jsonNode = objectMapper.readTree(jsonData);
        String schemaLocation = "classpath:schemas/non-existent-schema.json";

        when(mockJsonValidationProperties.getSchemaMappings()).thenReturn(Collections.singletonMap("source1", schemaLocation));
        Resource mockNonExistentResource = mock(Resource.class);
        when(mockNonExistentResource.exists()).thenReturn(false);
        when(mockResourceLoader.getResource(schemaLocation)).thenReturn(mockNonExistentResource);


        ValidationResult result = jsonProcessor.validate(jsonNode, "source1");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getValidationErrors()).hasSize(1);
        assertThat(result.getValidationErrors().get(0)).contains("Schema validation failed: Schema resource not found");
    }

    @Test
    void getSchema_cachingBehavior() throws IOException {
        String schemaJson = "{\"type\": \"string\"}";
        String schemaLocation = "classpath:schemas/cache-test-schema.json";

        Resource mockSchemaResource = new ByteArrayResource(schemaJson.getBytes(StandardCharsets.UTF_8));
        // Mock resource loader to return an InputStream provider
        when(mockResourceLoader.getResource(schemaLocation)).thenReturn(mockSchemaResource);


        // First call - should load and cache
        JsonSchema schema1 = jsonProcessor.getSchema(schemaLocation);
        assertThat(schema1).isNotNull();

        // Second call - should return from cache
        JsonSchema schema2 = jsonProcessor.getSchema(schemaLocation);
        assertThat(schema2).isSameAs(schema1); // Verify it's the same instance from cache

        // Verify resourceLoader was called only once if caching works
        verify(mockResourceLoader, Mockito.times(1)).getResource(schemaLocation);
    }
}
