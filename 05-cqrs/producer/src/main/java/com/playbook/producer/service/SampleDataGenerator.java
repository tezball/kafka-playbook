package com.playbook.producer.service;

import com.playbook.producer.model.ProductCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SampleDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(SampleDataGenerator.class);

    private final CatalogProducerService producerService;
    private final AtomicInteger productSequence = new AtomicInteger(1001);
    private final AtomicInteger commandSequence = new AtomicInteger(1);
    private final List<CreatedProduct> createdProducts = new ArrayList<>();

    private static final List<ProductTemplate> INITIAL_PRODUCTS = List.of(
            new ProductTemplate("4K Monitor", "Electronics", new BigDecimal("599.99")),
            new ProductTemplate("Wireless Mouse", "Accessories", new BigDecimal("49.99")),
            new ProductTemplate("Mechanical Keyboard", "Electronics", new BigDecimal("149.99")),
            new ProductTemplate("USB-C Hub", "Accessories", new BigDecimal("79.99")),
            new ProductTemplate("Standing Desk", "Office", new BigDecimal("449.99")),
            new ProductTemplate("Ergonomic Chair", "Office", new BigDecimal("349.99")),
            new ProductTemplate("Webcam HD Pro", "Electronics", new BigDecimal("129.99")),
            new ProductTemplate("Desk Lamp", "Office", new BigDecimal("39.99")),
            new ProductTemplate("Noise Cancelling Headphones", "Electronics", new BigDecimal("299.99")),
            new ProductTemplate("Laptop Stand", "Accessories", new BigDecimal("59.99")),
            new ProductTemplate("Thunderbolt Dock", "Electronics", new BigDecimal("249.99")),
            new ProductTemplate("Blue Light Glasses", "Accessories", new BigDecimal("29.99")),
            new ProductTemplate("Portable SSD 1TB", "Electronics", new BigDecimal("119.99")),
            new ProductTemplate("Cable Management Kit", "Accessories", new BigDecimal("19.99")),
            new ProductTemplate("Monitor Arm", "Office", new BigDecimal("89.99"))
    );

    private int initialProductIndex = 0;

    public SampleDataGenerator(CatalogProducerService producerService) {
        this.producerService = producerService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void sendPeriodicCommand() {
        ProductCommand command;

        // First, create the initial products one by one
        if (initialProductIndex < INITIAL_PRODUCTS.size()) {
            var template = INITIAL_PRODUCTS.get(initialProductIndex);
            initialProductIndex++;
            command = createProduct(template.name, template.category, template.price);
        } else if (createdProducts.isEmpty()) {
            // Fallback: create a random product if somehow the list is empty
            var template = INITIAL_PRODUCTS.get(ThreadLocalRandom.current().nextInt(INITIAL_PRODUCTS.size()));
            command = createProduct(template.name, template.category, template.price);
        } else {
            // Randomly update or delete an existing product
            var random = ThreadLocalRandom.current();
            double roll = random.nextDouble();

            if (roll < 0.7) {
                // 70%: UPDATE_PRICE
                var product = createdProducts.get(random.nextInt(createdProducts.size()));
                var newPrice = BigDecimal.valueOf(random.nextDouble(9.99, 999.99))
                        .setScale(2, RoundingMode.HALF_UP);
                command = new ProductCommand(
                        nextCommandId(),
                        product.productId,
                        "UPDATE_PRICE",
                        product.name,
                        product.category,
                        newPrice,
                        Instant.now()
                );
            } else if (roll < 0.85 && createdProducts.size() > 3) {
                // 15%: DELETE (only if we have enough products)
                var index = random.nextInt(createdProducts.size());
                var product = createdProducts.remove(index);
                command = new ProductCommand(
                        nextCommandId(),
                        product.productId,
                        "DELETE",
                        product.name,
                        product.category,
                        product.price,
                        Instant.now()
                );
            } else {
                // 15%: CREATE a new random product
                var template = INITIAL_PRODUCTS.get(random.nextInt(INITIAL_PRODUCTS.size()));
                var priceVariation = BigDecimal.valueOf(random.nextDouble(0.8, 1.3))
                        .setScale(2, RoundingMode.HALF_UP);
                var variedPrice = template.price.multiply(priceVariation).setScale(2, RoundingMode.HALF_UP);
                command = createProduct(template.name, template.category, variedPrice);
            }
        }

        log.info("Auto-sending command: {} {}", command.action(), command.productId());
        producerService.sendCommand(command);
    }

    public ProductCommand createProduct(String name, String category, BigDecimal price) {
        var productId = nextProductId();
        var command = new ProductCommand(
                nextCommandId(),
                productId,
                "CREATE",
                name,
                category,
                price,
                Instant.now()
        );
        createdProducts.add(new CreatedProduct(productId, name, category, price));
        return command;
    }

    public String nextProductId() {
        return "PROD-" + productSequence.getAndIncrement();
    }

    public String nextCommandId() {
        return "CMD-" + commandSequence.getAndIncrement();
    }

    public ProductCommand generateSampleCommand() {
        var random = ThreadLocalRandom.current();
        var template = INITIAL_PRODUCTS.get(random.nextInt(INITIAL_PRODUCTS.size()));
        return createProduct(template.name, template.category, template.price);
    }

    private record ProductTemplate(String name, String category, BigDecimal price) {}

    private record CreatedProduct(String productId, String name, String category, BigDecimal price) {}
}
