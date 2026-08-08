package com.lumira.api.competition;

/** Payload for the owner-bound side effect of a paid competition order. */
public record CompetitionPaymentEventRequest(
        String eventId,
        String orderNo,
        Long registrationId,
        Long ownerUserId,
        String ownerUserUuid
) {
}
