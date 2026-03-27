package com.playbook.enricher.model;

import java.time.Instant;

public record ClickEvent(
        String clickId,
        String userId,
        String page,
        String action,
        Instant timestamp
) {}
