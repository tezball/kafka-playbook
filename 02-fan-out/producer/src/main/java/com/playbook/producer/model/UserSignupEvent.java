package com.playbook.producer.model;

import java.time.Instant;

public record UserSignupEvent(
        String userId,
        String email,
        String name,
        String plan,
        Instant signedUpAt
) {}
