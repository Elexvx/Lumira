package com.lumira.api.event;

import java.util.Map;

/**
 * Writes a durable integration event in the caller's current database
 * transaction. A transaction rollback must roll this row back with the owner
 * mutation; relay and delivery happen later.
 */
public interface TransactionalEventOutboxPort {

    void record(
            String eventType,
            Long userId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> attributes
    );
}
