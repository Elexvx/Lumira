package com.lumira.domain.event;

/** Stable delivery states shared by every durable outbox implementation. */
public enum EventDeliveryStatus {
    PENDING,
    CLAIMED,
    PUBLISHED,
    RETRY_WAIT,
    DEAD_LETTER
}
