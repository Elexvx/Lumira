package com.lumira.api.event;

import java.util.Map;

/** Records a platform event in the caller's active transaction. */
public interface PlatformEventPort {

    void record(
            String sourceType,
            String eventType,
            Long userId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> attributes
    );
}
