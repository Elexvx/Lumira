package com.lumira.api.expert;

import java.time.LocalDateTime;

/** Expert-owned account-state update performed after System activates credentials. */
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
