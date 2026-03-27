package com.playbook.enricher.model;

import java.time.Instant;

public record EnrichedClick(
        String clickId,
        String userId,
        String userName,
        String userTier,
        String page,
        String action,
        Instant timestamp
) {}
