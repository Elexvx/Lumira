package com.lumira.job.domain.model;

import com.lumira.domain.model.ReadModel;
import java.time.Instant;

public final class JobDomainModels {

    private JobDomainModels() {
    }

    public record RelayTaskReadModel(
            String ownerContext,
            String eventType,
            int batchSize,
            Instant lastRunAt
    ) implements ReadModel {
    }
}
