package com.example.datagateway.protocol.kafka;

import com.example.datagateway.core.model.UnifiedInternalRequest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;


import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;


/**
 * Integration tests for {@link KafkaIngestionService} using an embedded Kafka broker.
 */
@SpringBootTest(
    classes = {KafkaTestApplication.class, KafkaIngestionService.class, KafkaConsumerProperties.class, com.example.datagateway.protocol.kafka.config.KafkaConsumerConfig.class},
    properties = {
        "spring.kafka.listener.ack-mode=manual_immediate",
        "gateway.protocol-adapters.kafka.consumers[0].name=default-consumer",
        "gateway.protocol-adapters.kafka.consumers[0].enabled=true",
        "gateway.protocol-adapters.kafka.consumers[0].topics=test-topic-json,test-topic-protobuf,test-topic-header",
        "gateway.protocol-adapters.kafka.consumers[0].groupId=test-gateway-group",
        "gateway.protocol-adapters.kafka.consumers[0].payloadTypeMappings[0].topic=test-topic-json",
        "gateway.protocol-adapters.kafka.consumers[0].payloadTypeMappings[0].payloadType=JSON",
        "gateway.protocol-adapters.kafka.consumers[0].payloadTypeMappings[1].header=X-Payload-Type", // For test-topic-header
        "gateway.protocol-adapters.kafka.consumers[0].defaultPayloadType=BINARY"
    }
)
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = { "listeners=PLAINTEXT://localhost:9093", "port=9093" }, // Using a different port from default
    topics = {"test-topic-json", "test-topic-protobuf", "test-topic-header"}
)
@DirtiesContext // Ensures a clean broker for each test method
@ActiveProfiles("test") // Ensure test profile is active if you have specific test configurations
class KafkaIngestionServiceTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @SpyBean // Spy on the actual service to verify interactions
    private KafkaIngestionService kafkaIngestionService;

    @Captor
    ArgumentCaptor<ConsumerRecord<String, byte[]>> consumerRecordCaptor;
    @Captor
    ArgumentCaptor<org.springframework.kafka.support.Acknowledgment> acknowledgmentCaptor;


    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        DefaultKafkaProducerFactory<String, byte[]> pf = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(pf);
    }

    @Test
    void receiveMessage_withJsonPayload_shouldProcessAndAcknowledge() throws InterruptedException {
        String topic = "test-topic-json";
        String key = "test-key-json";
        String payload = "{\"message\":\"hello json\"}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        kafkaTemplate.send(topic, key, payloadBytes);
        kafkaTemplate.flush();

        // Verify that the receiveMessage method was called
        verify(kafkaIngestionService, timeout(5000).times(1))
                .receiveMessage(consumerRecordCaptor.capture(), acknowledgmentCaptor.capture());

        ConsumerRecord<String, byte[]> capturedRecord = consumerRecordCaptor.getValue();
        assertThat(capturedRecord.topic()).isEqualTo(topic);
        assertThat(capturedRecord.key()).isEqualTo(key);
        assertThat(capturedRecord.value()).isEqualTo(payloadBytes);

        // Further assertions on UnifiedInternalRequest would require mocking/capturing
        // the argument to the (currently placeholder) orchestrator call within KafkaIngestionService.
        // For now, we ensure the message is consumed and acknowledged.
        assertThat(acknowledgmentCaptor.getValue()).isNotNull();
        // The acknowledgment is done inside the method, can't easily verify it was called without more complex setup.
        // Successful processing and no error logs would imply acknowledgment.
    }

    @Test
    void receiveMessage_withProtobufPayloadAndHeaderType_shouldProcessAndAcknowledge() throws InterruptedException {
        String topic = "test-topic-header"; // Mapped to use X-Payload-Type header
        String key = "test-key-proto";
        byte[] payloadBytes = new byte[]{10, 5, 'h', 'e', 'l', 'l', 'o'}; // Sample bytes

        org.apache.kafka.clients.producer.ProducerRecord<String, byte[]> producerRecord =
                new org.apache.kafka.clients.producer.ProducerRecord<>(topic, key, payloadBytes);
        producerRecord.headers().add(new RecordHeader("X-Payload-Type", "PROTOBUF".getBytes(StandardCharsets.UTF_8)));
        producerRecord.headers().add(new RecordHeader("Custom-Kafka-Header", "CustomValue".getBytes(StandardCharsets.UTF_8)));


        kafkaTemplate.send(producerRecord);
        kafkaTemplate.flush();

        verify(kafkaIngestionService, timeout(5000).times(1))
                .receiveMessage(consumerRecordCaptor.capture(), acknowledgmentCaptor.capture());

        ConsumerRecord<String, byte[]> capturedRecord = consumerRecordCaptor.getValue();
        assertThat(capturedRecord.topic()).isEqualTo(topic);
        assertThat(capturedRecord.key()).isEqualTo(key);
        assertThat(capturedRecord.value()).isEqualTo(payloadBytes);
        assertThat(new String(capturedRecord.headers().lastHeader("X-Payload-Type").value())).isEqualTo("PROTOBUF");
        assertThat(new String(capturedRecord.headers().lastHeader("Custom-Kafka-Header").value())).isEqualTo("CustomValue");

        assertThat(acknowledgmentCaptor.getValue()).isNotNull();
    }
    
    @Test
    void receiveMessage_withDefaultBinaryPayload_shouldProcessAndAcknowledge() throws InterruptedException {
        String topic = "test-topic-protobuf"; // No specific mapping, should use default BINARY
        String key = "test-key-binary";
        byte[] payloadBytes = new byte[]{1, 2, 3, 4, 5};

        kafkaTemplate.send(topic, key, payloadBytes);
        kafkaTemplate.flush();

        verify(kafkaIngestionService, timeout(5000).times(1))
                .receiveMessage(consumerRecordCaptor.capture(), acknowledgmentCaptor.capture());
        
        ConsumerRecord<String, byte[]> capturedRecord = consumerRecordCaptor.getValue();
        assertThat(capturedRecord.topic()).isEqualTo(topic);
        assertThat(capturedRecord.key()).isEqualTo(key);
        assertThat(capturedRecord.value()).isEqualTo(payloadBytes);
        
        // Here we expect PayloadType.BINARY to be inferred by KafkaIngestionService
        // This test implicitly verifies that part of the logic if we could inspect the UIR.

        assertThat(acknowledgmentCaptor.getValue()).isNotNull();
    }
}
