package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.event.EventConsumptionPort;
import com.lumira.saas.infrastructure.event.EventConsumptionGuard;

/** Adapts System's durable receipt implementation to the common consumption port. */
public class SystemEventConsumptionPort implements EventConsumptionPort {

    private final EventConsumptionGuard eventConsumptionGuard;

    public SystemEventConsumptionPort(EventConsumptionGuard eventConsumptionGuard) {
        this.eventConsumptionGuard = eventConsumptionGuard;
    }

    @Override
    public boolean executeOnce(EventIdentity event, Runnable sideEffect) {
        return eventConsumptionGuard.executeOnce(
                new EventConsumptionGuard.EventIdentity(
                        event.consumerName(),
                        event.eventId(),
                        event.eventType(),
                        event.sourceModule(),
                        event.aggregateId()
                ),
                sideEffect
        );
    }
}
