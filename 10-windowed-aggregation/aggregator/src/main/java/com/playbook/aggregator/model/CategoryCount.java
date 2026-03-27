package com.playbook.aggregator.model;

public record CategoryCount(
        String category,
        long count,
        String windowStart,
        String windowEnd
) {}
