package com.playbook.queryservice.service;

import com.playbook.queryservice.model.ProductCommand;
import com.playbook.queryservice.model.ProductView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProductMaterializerService {

    private static final Logger log = LoggerFactory.getLogger(ProductMaterializerService.class);

    private final ConcurrentHashMap<String, ProductView> productView = new ConcurrentHashMap<>();

    @KafkaListener(topics = "product-commands", groupId = "product-materializer-group")
    public void handleProductCommand(ProductCommand command) {
        switch (command.action()) {
            case "CREATE" -> {
                var view = new ProductView(
                        command.productId(),
                        command.name(),
                        command.category(),
                        command.price(),
                        command.timestamp()
                );
                productView.put(command.productId(), view);
                log.info("[MATERIALIZER] CREATE  | {} | {} ({}) | ${}",
                        command.productId(), command.name(), command.category(), command.price());
            }
            case "UPDATE_PRICE" -> {
                var existing = productView.get(command.productId());
                if (existing != null) {
                    var updated = new ProductView(
                            existing.productId(),
                            existing.name(),
                            existing.category(),
                            command.price(),
                            command.timestamp()
                    );
                    productView.put(command.productId(), updated);
                    log.info("[MATERIALIZER] UPDATE  | {} | {} -> price: ${}",
                            command.productId(), existing.name(), command.price());
                } else {
                    log.warn("[MATERIALIZER] UPDATE  | {} | product not found in view, skipping",
                            command.productId());
                }
            }
            case "DELETE" -> {
                var removed = productView.remove(command.productId());
                if (removed != null) {
                    log.info("[MATERIALIZER] DELETE  | {} | {} removed from catalog",
                            command.productId(), removed.name());
                } else {
                    log.warn("[MATERIALIZER] DELETE  | {} | product not found in view, skipping",
                            command.productId());
                }
            }
            default -> log.warn("[MATERIALIZER] Unknown action: {}", command.action());
        }
        log.info("[MATERIALIZER] View size: {} products", productView.size());
    }

    public Collection<ProductView> getAllProducts() {
        return productView.values();
    }

    public ProductView getProduct(String productId) {
        return productView.get(productId);
    }

    public List<ProductView> searchByCategory(String category) {
        return productView.values().stream()
                .filter(p -> p.category().equalsIgnoreCase(category))
                .toList();
    }
}
