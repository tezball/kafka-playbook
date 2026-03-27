package com.playbook.enricher.model;

public record UserProfile(
        String userId,
        String name,
        String email,
        String tier
) {}
