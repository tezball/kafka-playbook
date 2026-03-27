package com.playbook.profileproducer.model;

public record UserProfile(
        String userId,
        String name,
        String email,
        String tier
) {}
