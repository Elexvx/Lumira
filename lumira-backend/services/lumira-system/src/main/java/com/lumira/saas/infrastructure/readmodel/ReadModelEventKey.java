package com.lumira.saas.infrastructure.readmodel;

import org.springframework.util.StringUtils;

import java.util.UUID;

public final class ReadModelEventKey {

    private ReadModelEventKey() {
    }

    public static String unique(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            throw new IllegalArgumentException("eventType is required");
        }
        return eventType.trim() + ":" + UUID.randomUUID();
    }
}
