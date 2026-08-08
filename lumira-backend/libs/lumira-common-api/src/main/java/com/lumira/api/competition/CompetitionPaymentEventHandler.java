package com.lumira.api.competition;

/**
 * Fine-grained contract for the Competition-side effect of a paid order.
 * The async runtime depends on this contract rather than Competition's
 * implementation package.
 */
public interface CompetitionPaymentEventHandler {

    boolean handleOrderPaid(
            String eventId,
            String orderNo,
            Long registrationId,
            Long ownerUserId,
            String ownerUserUuid
    );
}
