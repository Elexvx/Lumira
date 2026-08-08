package com.lumira.api.expert;

/**
 * Expert-owned handling of an approved workflow event. The System runtime
 * translates its durable event representation into this owner-neutral record.
 */
public interface ExpertApprovalEventHandler {

    void handle(ExpertApprovalEvent event);

    record ExpertApprovalEvent(
            Long eventId,
            Long userId,
            String userUuid,
            String sourceType,
            String eventType,
            String eventKey,
            String payloadJson
    ) {
    }
}
