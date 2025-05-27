package com.example.datagateway.protocol.mqtt.config;

import com.example.datagateway.core.model.PayloadType;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty; // Using Jakarta validation
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for MQTT subscribers within the Data Gateway.
 * Allows defining multiple MQTT client configurations, each connecting to a broker
 * and subscribing to various topics.
 * <p>
 * Example YAML structure:
 * <pre>
 * gateway:
 *   protocol-adapters:
 *     mqtt:
 *       subscribers:
 *         - name: "default-iot-subscriber"
 *           enabled: true
 *           brokerUrl: "tcp://localhost:1883"
 *           clientIdPrefix: "gateway-sub" # Suffix will be added if not uniqueClientId provided
 *           # uniqueClientId: "myFixedClientId" # Use this for a fixed ID
 *           username: "user"
 *           passwordPlaceholder: "${MQTT_PASSWORD}" # For env var
 *           topics: ["sensor/+/data", "device/status/#"]
 *           qos: [1, 0] # QoS per topic, size must match topics list
 *           automaticReconnect: true
 *           cleanSession: true
 *           connectionTimeoutSec: 30
 *           keepAliveIntervalSec: 60
 *           defaultPayloadType: "JSON"
 *           payloadTypeMappings:
 *             - topicPattern: "sensor/+/raw"
 *               payloadType: "BINARY"
 *             - userProperty: "X-Payload-Type" # MQTTv5 user property
 * </pre>
 */
@ConfigurationProperties(prefix = "gateway.protocol-adapters.mqtt")
@Component
@Validated
@Getter
@Setter
public class MqttAdapterProperties {

    private List<MqttSubscriberConfig> subscribers = new ArrayList<>();

    @Data
    public static class MqttSubscriberConfig {
        /**
         * A unique name for this MQTT subscriber configuration.
         */
        @NotEmpty
        private String name;

        /**
         * Whether this MQTT subscriber is enabled and should attempt to connect.
         */
        private boolean enabled = true;

        /**
         * The URL of the MQTT broker (e.g., "tcp://localhost:1883", "ssl://mqtt.eclipseprojects.io:8883").
         */
        @NotEmpty
        private String brokerUrl;

        /**
         * A prefix for the client ID. A unique suffix (e.g., based on UUID or random string)
         * will be appended to this prefix to ensure client ID uniqueness if uniqueClientId is not set.
         * If uniqueClientId is set, this field is ignored.
         */
        private String clientIdPrefix = "gateway-sub";

        /**
         * A fully specified, unique client ID. If provided, this overrides clientIdPrefix.
         * Ensure this ID is unique across all clients connecting to the broker.
         */
        private String uniqueClientId;


        /**
         * Optional username for MQTT broker authentication.
         */
        private String username;

        /**
         * Placeholder for the password for MQTT broker authentication (e.g., "${MQTT_PASSWORD_ENV_VAR}").
         * The actual password should be supplied via an environment variable or secrets management.
         */
        private String passwordPlaceholder;

        /**
         * List of topic filters to subscribe to.
         */
        @NotEmpty
        @Size(min = 1)
        private List<String> topics = new ArrayList<>();

        /**
         * List of QoS levels corresponding to each topic filter in the {@code topics} list.
         * The size of this list must match the size of the {@code topics} list.
         * If not provided or empty, a default QoS of 1 will be used for all topics.
         */
        private List<Integer> qos = new ArrayList<>();

        /**
         * Whether the client should automatically attempt to reconnect to the server if the
         * connection is lost.
         */
        private boolean automaticReconnect = true;

        /**
         * Whether the client and server should establish a clean session.
         * A clean session means no state is preserved from previous sessions.
         */
        private boolean cleanSession = true;

        /**
         * Connection timeout in seconds.
         */
        private int connectionTimeoutSec = 30;

        /**
         * Keep alive interval in seconds. Defines the maximum time interval between messages
         * sent or received.
         */
        private int keepAliveIntervalSec = 60;


        /**
         * Default payload type to assume for messages received by this subscriber if not
         * determined by specific mappings or MQTT user properties.
         * Value should be one of the enum names from {@link com.example.datagateway.core.model.PayloadType}.
         */
        private String defaultPayloadType = "BINARY";

        /**
         * Specific mappings to determine payload type for consumed messages.
         * Evaluated in order. First match wins.
         */
        private List<PayloadTypeMapping> payloadTypeMappings = new ArrayList<>();

        public PayloadType getDefaultPayloadTypeEnum() {
            if (defaultPayloadType == null || defaultPayloadType.isBlank()) {
                return PayloadType.UNKNOWN;
            }
            try {
                return PayloadType.valueOf(defaultPayloadType.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Consider logging this misconfiguration
                return PayloadType.UNKNOWN;
            }
        }
    }

    @Data
    public static class PayloadTypeMapping {
        /**
         * A regex pattern to match against the MQTT topic. If the topic matches this pattern,
         * the specified {@code payloadType} is used.
         */
        private String topicPattern;

        /**
         * If using MQTTv5, check this MQTT User Property key. If the key exists, its value
         * (converted to uppercase) will be used to determine the {@link PayloadType}.
         */
        private String userProperty; // For MQTTv5 User Properties

        /**
         * The {@link PayloadType} to assign if this mapping rule matches.
         * Value should be one of the enum names from {@link com.example.datagateway.core.model.PayloadType}.
         */
        @NotNull
        private String payloadType;

        public PayloadType getPayloadTypeEnum() {
            try {
                return PayloadType.valueOf(payloadType.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Consider logging this misconfiguration
                return null; // Or a default/UNKNOWN
            }
        }
    }
}
