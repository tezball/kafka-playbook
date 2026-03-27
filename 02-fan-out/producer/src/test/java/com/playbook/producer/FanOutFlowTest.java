package com.playbook.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playbook.producer.model.UserSignupEvent;
import com.playbook.producer.service.SampleDataGenerator;
import com.playbook.producer.service.SignupProducerService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 3,
        topics = {"user-signups"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
class FanOutFlowTest {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Autowired
    private SignupProducerService signupProducerService;

    @MockBean
    @SuppressWarnings("unused")
    private SampleDataGenerator sampleDataGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private KafkaConsumer<String, String> createConsumerForGroup(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("user-signups"));
        return consumer;
    }

    private List<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> pollUntilRecords(
            KafkaConsumer<String, String> consumer, int expectedCount, Duration timeout) {
        var collected = new ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (collected.size() < expectedCount && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(collected::add);
        }
        return collected;
    }

    @Test
    @DisplayName("Given a user signup event is published, " +
            "when three consumer groups subscribe to the same topic, " +
            "then each group independently receives the event")
    void givenSignupPublished_whenThreeGroupsSubscribe_thenEachReceivesEvent() throws Exception {
        UserSignupEvent signup = new UserSignupEvent(
                "USR-FANOUT-001",
                "alice@example.com",
                "Alice Johnson",
                "PRO",
                Instant.now()
        );

        signupProducerService.sendSignup(signup);

        // Allow Kafka time to accept the message
        Thread.sleep(1000);

        // Three independent consumer groups -- simulating email, analytics, and audit services
        try (var emailConsumer = createConsumerForGroup("test-email-welcome-group");
             var analyticsConsumer = createConsumerForGroup("test-analytics-tracking-group");
             var auditConsumer = createConsumerForGroup("test-audit-log-group")) {

            var emailRecords = pollUntilRecords(emailConsumer, 1, Duration.ofSeconds(15));
            var analyticsRecords = pollUntilRecords(analyticsConsumer, 1, Duration.ofSeconds(15));
            var auditRecords = pollUntilRecords(auditConsumer, 1, Duration.ofSeconds(15));

            // Each group independently received the same event
            assertThat(emailRecords).hasSizeGreaterThanOrEqualTo(1);
            assertThat(analyticsRecords).hasSizeGreaterThanOrEqualTo(1);
            assertThat(auditRecords).hasSizeGreaterThanOrEqualTo(1);

            // Verify all three groups received the same payload
            for (var records : List.of(emailRecords, analyticsRecords, auditRecords)) {
                var matchingRecord = records.stream()
                        .filter(r -> r.key() != null && r.key().equals("USR-FANOUT-001"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Expected record with key USR-FANOUT-001"));

                var json = objectMapper.readTree(matchingRecord.value());
                assertThat(json.get("userId").asText()).isEqualTo("USR-FANOUT-001");
                assertThat(json.get("email").asText()).isEqualTo("alice@example.com");
                assertThat(json.get("name").asText()).isEqualTo("Alice Johnson");
                assertThat(json.get("plan").asText()).isEqualTo("PRO");
            }
        }
    }

    @Test
    @DisplayName("Given two signup events are published, " +
            "when each consumer group reads the topic, " +
            "then each group receives both events with independent offset tracking")
    void givenTwoSignups_whenGroupsRead_thenEachReceivesBothWithIndependentOffsets() throws Exception {
        UserSignupEvent signup1 = new UserSignupEvent(
                "USR-FANOUT-010",
                "bob@example.com",
                "Bob Smith",
                "FREE",
                Instant.now()
        );
        UserSignupEvent signup2 = new UserSignupEvent(
                "USR-FANOUT-011",
                "carol@example.com",
                "Carol White",
                "ENTERPRISE",
                Instant.now()
        );

        signupProducerService.sendSignup(signup1);
        signupProducerService.sendSignup(signup2);

        // Allow Kafka time to accept the messages
        Thread.sleep(1000);

        try (var group1 = createConsumerForGroup("test-independent-group-1");
             var group2 = createConsumerForGroup("test-independent-group-2");
             var group3 = createConsumerForGroup("test-independent-group-3")) {

            var records1 = pollUntilRecords(group1, 2, Duration.ofSeconds(15));
            var records2 = pollUntilRecords(group2, 2, Duration.ofSeconds(15));
            var records3 = pollUntilRecords(group3, 2, Duration.ofSeconds(15));

            // Each group received both events
            for (var records : List.of(records1, records2, records3)) {
                var keys = records.stream()
                        .map(org.apache.kafka.clients.consumer.ConsumerRecord::key)
                        .toList();
                assertThat(keys).contains("USR-FANOUT-010", "USR-FANOUT-011");
            }
        }
    }
}
