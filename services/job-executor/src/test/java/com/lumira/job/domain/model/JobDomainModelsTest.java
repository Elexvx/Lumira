package com.lumira.job.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.job.domain.model.JobDomainModels.RelayTaskReadModel;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JobDomainModelsTest {

    @Test
    void relayTaskReadModelDescribesOwnerContextWithoutBusinessRules() {
        RelayTaskReadModel task = new RelayTaskReadModel("payment", "payment.outbox", 200, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(task.ownerContext()).isEqualTo("payment");
        assertThat(task.eventType()).isEqualTo("payment.outbox");
        assertThat(task.batchSize()).isEqualTo(200);
    }
}
