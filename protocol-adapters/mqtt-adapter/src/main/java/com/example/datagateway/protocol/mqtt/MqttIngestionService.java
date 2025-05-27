package com.example.datagateway.protocol.mqtt;

import com.example.datagateway.core.model.PayloadType;
import com.example.datagateway.core.model.Protocol;
import com.example.datagateway.core.model.SourceInfo;
import com.example.datagateway.core.model.UnifiedInternalRequest;
import com.example.datagateway.protocol.mqtt.config.MqttAdapterProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service responsible for managing MQTT client connections, subscribing to topics,
 * and processing incoming MQTT messages. Converts messages to {@link UnifiedInternalRequest}
 * and (as a placeholder) logs them.
 */
@Service
@Slf4j
public class MqttIngestionService {

    private final MqttAdapterProperties adapterProperties;
    private final List<IMqttClient> mqttClients = new ArrayList<>();
    private final Environment environment; // For resolving password placeholders

    // In a real application, inject:
    // private final GatewayPipelineOrchestrator pipelineOrchestrator;

    /**
     * Constructs the MQTT Ingestion Service.
     *
     * @param adapterProperties The configuration properties for MQTT subscribers.
     * @param environment       The Spring environment for resolving property placeholders.
     */
    @Autowired
    public MqttIngestionService(MqttAdapterProperties adapterProperties, Environment environment) {
        this.adapterProperties = adapterProperties;
        this.environment = environment;
    }

    /**
     * Initializes and connects MQTT clients based on configuration properties.
     * This method is called after the bean has been constructed.
     */
    @PostConstruct
    public void initializeMqttClients() {
        if (adapterProperties.getSubscribers() == null) {
            log.info("No MQTT subscribers configured.");
            return;
        }

        for (MqttAdapterProperties.MqttSubscriberConfig subConfig : adapterProperties.getSubscribers()) {
            if (!subConfig.isEnabled()) {
                log.info("MQTT subscriber '{}' is disabled.", subConfig.getName());
                continue;
            }

            String clientId = subConfig.getUniqueClientId();
            if (clientId == null || clientId.isBlank()) {
                clientId = subConfig.getClientIdPrefix() + "-" + UUID.randomUUID().toString().substring(0, 8);
            }

            try {
                IMqttClient client = new MqttClient(subConfig.getBrokerUrl(), clientId, new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(subConfig.isAutomaticReconnect());
                options.setCleanSession(subConfig.isCleanSession());
                options.setConnectionTimeout(subConfig.getConnectionTimeoutSec());
                options.setKeepAliveInterval(subConfig.getKeepAliveIntervalSec());

                if (subConfig.getUsername() != null && !subConfig.getUsername().isBlank()) {
                    options.setUserName(subConfig.getUsername());
                }
                if (subConfig.getPasswordPlaceholder() != null && !subConfig.getPasswordPlaceholder().isBlank()) {
                    String password = environment.getProperty(
                            subConfig.getPasswordPlaceholder().replace("${", "").replace("}", "")
                    );
                    if (password != null) {
                        options.setPassword(password.toCharArray());
                    } else {
                        log.warn("MQTT password placeholder '{}' could not be resolved for subscriber '{}'.",
                                subConfig.getPasswordPlaceholder(), subConfig.getName());
                    }
                }

                client.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        log.info("MQTT client '{}' connected to {}. Reconnect: {}", client.getClientId(), serverURI, reconnect);
                        subscribeToTopics(client, subConfig);
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        log.warn("MQTT client '{}' connection lost: {}", client.getClientId(), cause.getMessage(), cause);
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        try {
                            handleMqttMessage(client.getClientId(), topic, message, subConfig);
                        } catch (Exception e) {
                            log.error("Error processing MQTT message from topic '{}' for client '{}': {}",
                                    topic, client.getClientId(), e.getMessage(), e);
                        }
                    }

                    @Override
                    public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                        // Not used for subscribers, mainly for publishers
                    }
                });

                log.info("Connecting MQTT client '{}' ({}) to broker at {}", subConfig.getName(), client.getClientId(), subConfig.getBrokerUrl());
                client.connect(options);
                mqttClients.add(client);

            } catch (MqttException e) {
                log.error("Error initializing MQTT client '{}' for broker {}: {}",
                        subConfig.getName(), subConfig.getBrokerUrl(), e.getMessage(), e);
            }
        }
    }

    private void subscribeToTopics(IMqttClient client, MqttAdapterProperties.MqttSubscriberConfig subConfig) {
        List<String> topics = subConfig.getTopics();
        List<Integer> qosLevels = subConfig.getQos();

        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            int qos = (qosLevels != null && i < qosLevels.size()) ? qosLevels.get(i) : 1; // Default QoS 1
            try {
                client.subscribe(topic, qos);
                log.info("MQTT client '{}' subscribed to topic '{}' with QoS {}", client.getClientId(), topic, qos);
            } catch (MqttException e) {
                log.error("Error subscribing MQTT client '{}' to topic '{}': {}", client.getClientId(), topic, e.getMessage(), e);
            }
        }
    }

    private void handleMqttMessage(String gatewayClientId, String topic, MqttMessage message, MqttAdapterProperties.MqttSubscriberConfig subConfig) {
        long receivedTimestamp = Instant.now().toEpochMilli();
        String requestId = UUID.randomUUID().toString(); // New request ID for each message

        byte[] payloadBytes = message.getPayload();
        PayloadType payloadType = determinePayloadType(topic, message, subConfig);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("mqtt_topic", topic);
        metadata.put("mqtt_qos", String.valueOf(message.getQos()));
        metadata.put("mqtt_retained", String.valueOf(message.isRetained()));
        metadata.put("mqtt_duplicate", String.valueOf(message.isDuplicate()));
        metadata.put("mqtt_message_id", String.valueOf(message.getId())); // Paho's internal message ID

        // For MQTTv5, user properties would be extracted here if using Paho MQTTv5 client
        // Example with Paho v5 (conceptual):
        // if (message instanceof org.eclipse.paho.mqttv5.common.MqttMessage) {
        //     org.eclipse.paho.mqttv5.common.packet.MqttProperties props = ((org.eclipse.paho.mqttv5.common.MqttMessage) message).getProperties();
        //     if (props != null && props.getUserProperties() != null) {
        //         props.getUserProperties().forEach(p -> metadata.put("mqtt_userprop_" + p.getKey(), p.getValue()));
        //     }
        // }


        SourceInfo sourceInfo = SourceInfo.builder()
                .protocol(Protocol.MQTT)
                .sourceAddress("mqtt-broker:" + subConfig.getBrokerUrl()) // Could be more specific if broker provides client source IP
                .requestTarget(topic) // Topic as the request target
                .build();

        UnifiedInternalRequest unifiedRequest = UnifiedInternalRequest.builder()
                .requestId(requestId)
                .payload(payloadBytes)
                .payloadType(payloadType)
                .sourceInfo(sourceInfo)
                .metadata(metadata)
                .receivedTimestamp(receivedTimestamp)
                .originalEncoding(payloadType == PayloadType.JSON || payloadType == PayloadType.XML || payloadType == PayloadType.TEXT || payloadType == PayloadType.CSV ? StandardCharsets.UTF_8.name() : null)
                .build();

        log.info("MQTT Adapter: Client '{}' received message. Created UnifiedInternalRequest: RequestId={}, SourceTopic={}, PayloadType={}, Size={}",
                gatewayClientId,
                topic,
                unifiedRequest.getPayloadType(),
                (payloadBytes != null ? payloadBytes.length : 0));
        log.debug("MQTT Adapter: UnifiedInternalRequest details: {}", unifiedRequest);

        // Placeholder for orchestrator integration:
        // pipelineOrchestrator.processRequest(Mono.just(unifiedRequest)).subscribe();
    }

    private PayloadType determinePayloadType(String topic, MqttMessage message, MqttAdapterProperties.MqttSubscriberConfig subConfig) {
        // Strategy 1: Check MQTTv5 User Properties (if available and configured)
        // This requires Paho MQTTv5 client and MqttMessage v5 type.
        // For Paho v3, this part is conceptual.
        if (subConfig.getPayloadTypeMappings() != null) {
            for (MqttAdapterProperties.PayloadTypeMapping mapping : subConfig.getPayloadTypeMappings()) {
                if (mapping.getUserProperty() != null && !mapping.getUserProperty().isBlank()) {
                    // Conceptual for Paho v3; Paho v5 MqttMessage has getProperties().getUserProperties()
                    // String userPropValue = getMqttUserProperty(message, mapping.getUserProperty());
                    // if (userPropValue != null) {
                    //     try { return PayloadType.valueOf(userPropValue.toUpperCase()); }
                    //     catch (IllegalArgumentException e) { log.warn("Invalid PayloadType from user property...");}
                    // }
                    // As a simple simulation for v3, we can check if a metadata field added by a publisher exists
                    // (though not standard MQTT user properties)
                }
            }
        }


        // Strategy 2: Check topic pattern mappings
        if (subConfig.getPayloadTypeMappings() != null) {
            for (MqttAdapterProperties.PayloadTypeMapping mapping : subConfig.getPayloadTypeMappings()) {
                if (mapping.getTopicPattern() != null && !mapping.getTopicPattern().isBlank()) {
                    if (Pattern.matches(mapping.getTopicPattern().replace("#", ".*").replace("+", "[^/]+"), topic)) {
                        if (mapping.getPayloadTypeEnum() != null) {
                            return mapping.getPayloadTypeEnum();
                        }
                    }
                }
            }
        }

        // Strategy 3: Default payload type for the subscriber
        return subConfig.getDefaultPayloadTypeEnum();
    }


    /**
     * Disconnects MQTT clients during application shutdown.
     */
    @PreDestroy
    public void disconnectClients() {
        log.info("Disconnecting {} MQTT clients...", mqttClients.size());
        for (IMqttClient client : mqttClients) {
            if (client.isConnected()) {
                try {
                    // Using disconnectForcibly to ensure it doesn't block indefinitely if broker is unresponsive
                    client.disconnectForcibly(1000); // timeout 1 sec
                    log.info("MQTT client '{}' disconnected.", client.getClientId());
                } catch (MqttException e) {
                    log.warn("Error disconnecting MQTT client '{}': {}", client.getClientId(), e.getMessage());
                }
            }
            try {
                client.close(); // Release resources
            } catch (MqttException e) {
                 log.warn("Error closing MQTT client '{}': {}", client.getClientId(), e.getMessage());
            }
        }
        mqttClients.clear();
    }
}
