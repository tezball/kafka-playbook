package com.playbook.consumer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsumerConfig {

    @Value("${consumer.region:NA}")
    private String region;

    @Value("${CONSUMER_GROUP:na-region-group}")
    private String groupId;

    public String getRegion() {
        return region;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getRegionDisplayName() {
        return switch (region) {
            case "NA" -> "North America";
            case "EU" -> "Europe";
            case "APAC" -> "Asia-Pacific";
            default -> region;
        };
    }
}
