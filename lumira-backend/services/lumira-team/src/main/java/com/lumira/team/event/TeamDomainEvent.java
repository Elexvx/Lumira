package com.lumira.team.event;

import java.time.LocalDateTime;

public interface TeamDomainEvent {
    Long teamId();

    LocalDateTime occurredAt();
}
