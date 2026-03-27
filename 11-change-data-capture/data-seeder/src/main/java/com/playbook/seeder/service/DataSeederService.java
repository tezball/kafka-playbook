package com.playbook.seeder.service;

import com.playbook.seeder.model.Product;
import com.playbook.seeder.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DataSeederService {

    private static final Logger log = LoggerFactory.getLogger(DataSeederService.class);

    private final ProductRepository productRepository;

    private static final List<String> PRODUCT_NAMES = List.of(
            "Bluetooth Speaker", "Webcam HD Pro", "USB-C Hub",
            "Noise Cancelling Earbuds", "Portable SSD 1TB", "Microphone Kit",
            "Laptop Stand", "Blue Light Glasses", "Wireless Mouse",
            "Thunderbolt Dock", "Mechanical Keyboard", "27\" 4K Display"
    );

    private static final List<String> CATEGORIES = List.of(
            "Electronics", "Accessories", "Furniture", "Audio", "Storage"
    );

    private static final List<BigDecimal> PRICES = List.of(
            new BigDecimal("29.99"), new BigDecimal("49.99"), new BigDecimal("89.99"),
            new BigDecimal("129.99"), new BigDecimal("199.99"), new BigDecimal("299.99"),
            new BigDecimal("399.99"), new BigDecimal("549.99")
    );

    public DataSeederService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 15000)
    public void performRandomChange() {
        var random = ThreadLocalRandom.current();
        int action = random.nextInt(3);

        switch (action) {
            case 0 -> insertRandomProduct();
            case 1 -> updateRandomPrice();
            case 2 -> toggleRandomStock();
        }
    }

    public String performManualChange() {
        var random = ThreadLocalRandom.current();
        int action = random.nextInt(3);

        return switch (action) {
            case 0 -> insertRandomProduct();
            case 1 -> updateRandomPrice();
            case 2 -> toggleRandomStock();
            default -> "No action";
        };
    }

    private String insertRandomProduct() {
        var random = ThreadLocalRandom.current();
        var name = PRODUCT_NAMES.get(random.nextInt(PRODUCT_NAMES.size()));
        var category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
        var price = PRICES.get(random.nextInt(PRICES.size()));

        var product = new Product(name, category, price);
        productRepository.save(product);

        log.info("""

                ============================================
                  SEEDER — INSERT
                --------------------------------------------
                  Product: {} | {} | ${}
                ============================================""",
                name, category, price);

        return "Inserted: " + product;
    }

    private String updateRandomPrice() {
        var products = productRepository.findAll();
        if (products.isEmpty()) return insertRandomProduct();

        var random = ThreadLocalRandom.current();
        var product = products.get(random.nextInt(products.size()));
        var oldPrice = product.getPrice();

        // Generate a new price: +/- 10-30% of current price
        double factor = 0.7 + (random.nextDouble() * 0.6); // 0.7 to 1.3
        var newPrice = oldPrice.multiply(BigDecimal.valueOf(factor))
                .setScale(2, RoundingMode.HALF_UP);
        product.setPrice(newPrice);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("""

                ============================================
                  SEEDER — UPDATE PRICE
                --------------------------------------------
                  Product: {} (id={})
                  Price:   ${} -> ${}
                ============================================""",
                product.getName(), product.getId(), oldPrice, newPrice);

        return "Updated price: " + product.getName() + " $" + oldPrice + " -> $" + newPrice;
    }

    private String toggleRandomStock() {
        var products = productRepository.findAll();
        if (products.isEmpty()) return insertRandomProduct();

        var random = ThreadLocalRandom.current();
        var product = products.get(random.nextInt(products.size()));
        var wasInStock = product.getInStock();
        product.setInStock(!wasInStock);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("""

                ============================================
                  SEEDER — TOGGLE STOCK
                --------------------------------------------
                  Product: {} (id={})
                  Stock:   {} -> {}
                ============================================""",
                product.getName(), product.getId(), wasInStock, !wasInStock);

        return "Toggled stock: " + product.getName() + " " + wasInStock + " -> " + !wasInStock;
    }
}
