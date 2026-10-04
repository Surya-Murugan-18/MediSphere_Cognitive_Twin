package com.medisphere.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

import static com.medisphere.kafka.KafkaTopics.*;

/**
 * Kafka producer + consumer factory configuration.
 *
 * Activated once spring-kafka is on the classpath. Reads KAFKA_BOOTSTRAP_SERVERS
 * from the environment (defaults to localhost:9092 for local dev).
 *
 * Consumer group: medisphere-backend  (per design.md §3.5)
 * Auto-offset-reset: earliest  (so a restarted consumer replays unprocessed messages)
 * Acknowledgement: RECORD-level manual ack for the vitals.raw listener so that
 * persistence failures do not silently swallow messages.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:medisphere-backend}")
    private String groupId;

    // ── Producer ────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Include type header so the consumer can deserialize correctly
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        // Idempotent producer reduces duplicate delivery risk
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer ────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.medisphere.*,java.util,java.lang");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        // Deserialize into generic Map so each consumer controls its own mapping
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Manual acknowledgement so we control offset commit after successful processing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(1); // single-partition dev setup; bump for production
        return factory;
    }

    // ── Topic auto-creation (dev convenience) ───────────────────────────
    // These only take effect when the AdminClient can reach the broker.
    // In test environments using EmbeddedKafka the topics are created by the test annotation.

    @Bean public NewTopic topicVitalsRaw()      { return TopicBuilder.name(VITALS_RAW).partitions(1).replicas(1).build(); }
    @Bean public NewTopic topicVitalsAnomaly()  { return TopicBuilder.name(VITALS_ANOMALY).partitions(1).replicas(1).build(); }
    @Bean public NewTopic topicAlertCreated()   { return TopicBuilder.name(ALERT_CREATED).partitions(1).replicas(1).build(); }
    @Bean public NewTopic topicTwinUpdate()     { return TopicBuilder.name(TWIN_UPDATE).partitions(1).replicas(1).build(); }
    @Bean public NewTopic topicFederatedRound() { return TopicBuilder.name(FEDERATED_ROUND).partitions(1).replicas(1).build(); }
    @Bean public NewTopic topicCarePlanApproved() { return TopicBuilder.name(CAREPLAN_APPROVED).partitions(1).replicas(1).build(); }
    @Bean public NewTopic topicFhirIngested()   { return TopicBuilder.name(FHIR_INGESTED).partitions(1).replicas(1).build(); }
    @Bean public NewTopic topicReportReady()    { return TopicBuilder.name(REPORT_READY).partitions(1).replicas(1).build(); }
}
