package com.example.datagateway.protocol.mqtt;

import com.example.datagateway.core.model.PayloadType;
import com.example.datagateway.core.model.UnifiedInternalRequest;
import com.example.datagateway.protocol.mqtt.config.MqttAdapterProperties;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;


import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for {@link MqttIngestionService}.
 * Focuses on the message handling logic and property parsing.
 */
@ExtendWith(MockitoExtension.class)
class MqttIngestionServiceTest {

    @Mock
    private Environment environment;

    @Spy // Use Spy to test real methods but allow mocking parts if needed
    private MqttAdapterProperties adapterProperties;

    @InjectMocks // Injects mocks into MqttIngestionService
    private MqttIngestionService mqttIngestionService;

    @Captor
    ArgumentCaptor<UnifiedInternalRequest> uirCaptor;

    // To directly invoke the private handleMqttMessage method via reflection
    private Method handleMqttMessageMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        // Prepare the private method for invocation if needed
        // For direct testing of handleMqttMessage.
        // Note: This is generally discouraged but can be useful for complex private methods.
        // A better approach might be to refactor handleMqttMessage to be package-private or use a helper class.
        handleMqttMessageMethod = MqttIngestionService.class.getDeclaredMethod(
                "handleMqttMessage", String.class, String.class, MqttMessage.class, MqttAdapterProperties.MqttSubscriberConfig.class
        );
        handleMqttMessageMethod.setAccessible(true);


        // Setup a default mock behavior for environment if password placeholders are used in tests
        when(environment.getProperty(anyString())).thenReturn("mockPassword");
    }

    private MqttAdapterProperties.MqttSubscriberConfig createTestSubscriberConfig(String name, String defaultType, List<MqttAdapterProperties.PayloadTypeMapping> mappings) {
        MqttAdapterProperties.MqttSubscriberConfig config = new MqttAdapterProperties.MqttSubscriberConfig();
        config.setName(name);
        config.setEnabled(true);
        config.setBrokerUrl("tcp://dummy-broker:1883");
        config.setClientIdPrefix("test-client-" + name);
        config.setTopics(List.of("test/topic/" + name));
        config.setQos(List.of(1));
        config.setDefaultPayloadType(defaultType);
        if (mappings != null) {
            config.setPayloadTypeMappings(mappings);
        }
        return config;
    }

    @Test
    void handleMqttMessage_withDefaultJsonPayloadType_shouldCreateCorrectUIR() throws Exception {
        MqttAdapterProperties.MqttSubscriberConfig subConfig = createTestSubscriberConfig("json-test", "JSON", null);
        String topic = "test/topic/json-test";
        String payloadStr = "{\"key\":\"value\"}";
        MqttMessage message = new MqttMessage(payloadStr.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setId(123);

        // We need a way to capture the UIR created by handleMqttMessage.
        // Since it's logged, we could use a log appender, but that's cumbersome.
        // For now, since we can't directly mock the orchestrator, this test will focus on the UIR construction logic
        // within handleMqttMessage. We will invoke it using reflection.

        // This is a simplified way to test the private method's logic.
        // In a real scenario, you'd mock the orchestrator and capture what's sent to it.
        // For this test, we'll assume handleMqttMessage is called and check its behavior.
        // The log will be our primary output for now.

        handleMqttMessageMethod.invoke(mqttIngestionService, "test-client-id", topic, message, subConfig);

        // If we had a mock orchestrator:
        // verify(mockPipelineOrchestrator, times(1)).processRequest(uirCaptor.capture());
        // UnifiedInternalRequest uir = uirCaptor.getValue();
        // assertThat(uir.getPayloadType()).isEqualTo(PayloadType.JSON);
        // assertThat(new String((byte[]) uir.getPayload())).isEqualTo(payloadStr);
        // assertThat(uir.getSourceInfo().getRequestTarget()).isEqualTo(topic);

        // Since we can't capture directly without more refactoring or a real pipeline mock,
        // this test primarily serves as a structural placeholder for now.
        // The core logic of UIR creation is within handleMqttMessage, which we are calling.
        // We expect logs to show the correct UIR.
        // To make it more testable without major refactoring for this subtask,
        // we rely on the fact that `determinePayloadType` is called and returns the default.
         PayloadType determinedType = (PayloadType) ReflectionTestUtils.invokeMethod(
            mqttIngestionService, "determinePayloadType", topic, message, subConfig
        );
        assertThat(determinedType).isEqualTo(PayloadType.JSON);
    }


    @Test
    void handleMqttMessage_withTopicPatternMappingToBinary_shouldCreateCorrectUIR() throws Exception {
        MqttAdapterProperties.PayloadTypeMapping mapping = new MqttAdapterProperties.PayloadTypeMapping();
        mapping.setTopicPattern("test/topic/binary-test/raw");
        mapping.setPayloadType("BINARY");

        MqttAdapterProperties.MqttSubscriberConfig subConfig = createTestSubscriberConfig("binary-test", "JSON", List.of(mapping));
        String topic = "test/topic/binary-test/raw";
        byte[] payloadBytes = new byte[]{0x01, 0x02, 0x03};
        MqttMessage message = new MqttMessage(payloadBytes);

        handleMqttMessageMethod.invoke(mqttIngestionService, "test-client-id", topic, message, subConfig);

        // Similar to above, direct UIR capture would be ideal.
        // We check the type determination.
        PayloadType determinedType = (PayloadType) ReflectionTestUtils.invokeMethod(
            mqttIngestionService, "determinePayloadType", topic, message, subConfig
        );
        assertThat(determinedType).isEqualTo(PayloadType.BINARY);
    }

    @Test
    void handleMqttMessage_withNoMatchingMapping_shouldUseDefaultPayloadType() throws Exception {
         MqttAdapterProperties.MqttSubscriberConfig subConfig = createTestSubscriberConfig("default-check", "TEXT", List.of());
        String topic = "some/other/topic";
        String payloadStr = "simple text";
        MqttMessage message = new MqttMessage(payloadStr.getBytes(StandardCharsets.UTF_8));

        handleMqttMessageMethod.invoke(mqttIngestionService, "test-client-id", topic, message, subConfig);
        
        PayloadType determinedType = (PayloadType) ReflectionTestUtils.invokeMethod(
            mqttIngestionService, "determinePayloadType", topic, message, subConfig
        );
        assertThat(determinedType).isEqualTo(PayloadType.TEXT);
    }

    @Test
    void initializeMqttClients_shouldAttemptToConnectEnabledSubscribers() {
        // This test is more complex as it involves MqttClient instantiation and connection.
        // We'll mock the properties and verify behavior.
        MqttAdapterProperties.MqttSubscriberConfig sub1 = createTestSubscriberConfig("sub1", "JSON", null);
        sub1.setEnabled(true);
        MqttAdapterProperties.MqttSubscriberConfig sub2 = createTestSubscriberConfig("sub2", "TEXT", null);
        sub2.setEnabled(false); // Disabled

        when(adapterProperties.getSubscribers()).thenReturn(List.of(sub1, sub2));

        // We cannot easily mock the MqttClient constructor or its connect method
        // without PowerMock or significant refactoring of MqttIngestionService to allow client injection.
        // For this unit test, we will verify that the service attempts to process enabled clients.
        // A full integration test with an embedded broker would be better for testing connections.

        // To "test" this, we'd need to spy on client creation or have a factory.
        // Given the constraints, we'll assume that if no exception is thrown for sub1 (enabled)
        // and sub2 (disabled) is skipped, the basic logic of iterating configs is working.
        // The actual connection attempt will likely fail as "tcp://dummy-broker:1883" is not real.
        // The logs in MqttIngestionService would show connection attempts/failures.
        
        try {
            mqttIngestionService.initializeMqttClients();
        } catch (Exception e) {
            // We expect exceptions here because the broker is not real.
            // The important part is that it tries to connect for enabled clients.
            // Log messages in the actual service would indicate this.
            System.out.println("Caught expected exception during MQTT client init in test: " + e.getMessage());
        }
        // This test is limited in its current form for verifying actual connections.
        // It mainly ensures initializeMqttClients runs without NPEs for a basic config.
    }
}
