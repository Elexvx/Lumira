package com.lumira.api.expert;

import java.time.LocalDateTime;

/**
 * System/account outbound port for the expert-owned state update that follows
 * credential activation. The account-activation token and user credential
 * lifecycle remain owned by System; Expert only implements this narrow port.
 */
public interface ExpertAccountActivationPort {

    int activate(ExpertAccountActivation activation);

    record ExpertAccountActivation(
            Long expertId,
            Long userId,
            String userUuid,
            LocalDateTime activatedAt
    ) {
    }
}
