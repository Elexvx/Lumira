package com.lumira.domain.event;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationEventEnvelopeTest {

    @Test
    void normalizesAndDefaultsDurableContractFields() {
        IntegrationEventEnvelope event = new IntegrationEventEnvelope(
                null, " file.object.uploaded.v1 ", 0, " file ", 7L,
                " file.object ", " 42 ", null, " trace ", null, null,
                Map.of("fileId", 42L)
        );

        assertThat(event.eventId()).isNotNull();
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.eventType()).isEqualTo("file.object.uploaded.v1");
        assertThat(event.orderingKey()).isEqualTo("42");
        assertThat(event.payload()).containsEntry("fileId", 42L);
    }

    @Test
    void rejectsInvalidTenantAndRequiredIdentity() {
        assertThatThrownBy(() -> new IntegrationEventEnvelope(
                null, "event.v1", 1, "file", 0L, "file.object", "42",
                null, null, null, null, Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
