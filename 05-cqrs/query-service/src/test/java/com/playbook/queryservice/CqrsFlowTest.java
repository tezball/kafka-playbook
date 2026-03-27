package com.playbook.queryservice;

import com.playbook.queryservice.model.ProductCommand;
import com.playbook.queryservice.model.ProductView;
import com.playbook.queryservice.service.ProductMaterializerService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class CqrsFlowTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private ProductMaterializerService materializerService;

    private KafkaTemplate<String, ProductCommand> kafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(JsonSerializer.TYPE_MAPPINGS,
                "productCommand:com.playbook.queryservice.model.ProductCommand");

        DefaultKafkaProducerFactory<String, ProductCommand> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @Test
    @DisplayName("Given a CREATE product command is published, " +
            "when the materializer processes it, " +
            "then the product appears in the materialized view")
    void givenCreateCommand_whenMaterializerProcesses_thenProductAppearsInView() {
        String productId = "PROD-CREATE-" + UUID.randomUUID();
        String name = "4K Monitor";
        String category = "Electronics";
        BigDecimal price = new BigDecimal("599.99");

        ProductCommand createCommand = new ProductCommand(
                "CMD-" + UUID.randomUUID(), productId, "CREATE",
                name, category, price, Instant.now());

        kafkaTemplate.send("product-commands", productId, createCommand);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ProductView product = materializerService.getProduct(productId);
            assertThat(product).isNotNull();
            assertThat(product.productId()).isEqualTo(productId);
            assertThat(product.name()).isEqualTo(name);
            assertThat(product.category()).isEqualTo(category);
            assertThat(product.price()).isEqualByComparingTo(price);
        });
    }

    @Test
    @DisplayName("Given a product exists and an UPDATE_PRICE command is published, " +
            "when the materializer processes it, " +
            "then the product's price is updated in the view")
    void givenExistingProduct_whenUpdatePriceCommand_thenPriceIsUpdated() {
        String productId = "PROD-UPDATE-" + UUID.randomUUID();
        BigDecimal originalPrice = new BigDecimal("599.99");
        BigDecimal updatedPrice = new BigDecimal("449.99");

        // First, create the product
        ProductCommand createCommand = new ProductCommand(
                "CMD-" + UUID.randomUUID(), productId, "CREATE",
                "Wireless Mouse", "Accessories", originalPrice, Instant.now());
        kafkaTemplate.send("product-commands", productId, createCommand);

        // Wait for the product to appear
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(materializerService.getProduct(productId)).isNotNull()
        );

        // Now update the price
        ProductCommand updateCommand = new ProductCommand(
                "CMD-" + UUID.randomUUID(), productId, "UPDATE_PRICE",
                null, null, updatedPrice, Instant.now());
        kafkaTemplate.send("product-commands", productId, updateCommand);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ProductView product = materializerService.getProduct(productId);
            assertThat(product).isNotNull();
            assertThat(product.price()).isEqualByComparingTo(updatedPrice);
            // Name and category should be preserved from the original CREATE
            assertThat(product.name()).isEqualTo("Wireless Mouse");
            assertThat(product.category()).isEqualTo("Accessories");
        });
    }

    @Test
    @DisplayName("Given a product exists and a DELETE command is published, " +
            "when the materializer processes it, " +
            "then the product is removed from the view")
    void givenExistingProduct_whenDeleteCommand_thenProductIsRemovedFromView() {
        String productId = "PROD-DELETE-" + UUID.randomUUID();

        // First, create the product
        ProductCommand createCommand = new ProductCommand(
                "CMD-" + UUID.randomUUID(), productId, "CREATE",
                "Desk Lamp", "Home", new BigDecimal("39.99"), Instant.now());
        kafkaTemplate.send("product-commands", productId, createCommand);

        // Wait for the product to appear
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(materializerService.getProduct(productId)).isNotNull()
        );

        // Now delete it
        ProductCommand deleteCommand = new ProductCommand(
                "CMD-" + UUID.randomUUID(), productId, "DELETE",
                null, null, null, Instant.now());
        kafkaTemplate.send("product-commands", productId, deleteCommand);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(materializerService.getProduct(productId)).isNull()
        );
    }
}
