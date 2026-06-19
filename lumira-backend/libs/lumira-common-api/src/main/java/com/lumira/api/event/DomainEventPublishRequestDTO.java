package com.lumira.api.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DomainEventPublishRequestDTO(
        @NotEmpty List<@Valid DomainEventEnvelopeDTO> events
) {
}
