package com.lumira.api.event;

/** Executes a consumer side effect and its idempotency receipt atomically. */
public interface EventConsumptionPort {

    boolean executeOnce(EventIdentity event, Runnable sideEffect);

    record EventIdentity(
            String consumerName,
            String eventId,
            String eventType,
            String sourceModule,
            String aggregateId
    ) {
        public EventIdentity {
            consumerName = requireText(consumerName, "consumerName");
            eventId = requireText(eventId, "eventId");
            eventType = requireText(eventType, "eventType");
            sourceModule = requireText(sourceModule, "sourceModule");
            aggregateId = requireText(aggregateId, "aggregateId");
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value.trim();
        }
    }
}
