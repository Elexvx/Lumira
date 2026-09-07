package com.lumira.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

/** XXL-JOB adapter for explicit, fenced recovery only. */
@Component
@ConditionalOnLumiraAsyncEnabled
public class OutboxRecoveryJobHandler {
    private final BackendJobClient backendJobClient;
    private final ObjectMapper objectMapper;

    public OutboxRecoveryJobHandler(BackendJobClient backendJobClient, ObjectMapper objectMapper) {
        this.backendJobClient = backendJobClient;
        this.objectMapper = objectMapper;
    }

    @XxlJob("outboxEventReplayJob")
    public void replayEvent() {
        RecoveryRequest request = request(true);
        int delivered = backendJobClient.replayOutboxEvent(
                request.owner(), request.eventId(), request.operationEpoch(), request.fenceToken()
        );
        XxlJobHelper.log("fenced outbox replay owner={} eventId={} delivered={}", request.owner(), request.eventId(), delivered);
    }

    @XxlJob("staleOutboxRecoveryJob")
    public void recoverStale() {
        RecoveryRequest request = request(false);
        int delivered = backendJobClient.recoverStaleOutbox(request.owner(), request.operationEpoch(), request.fenceToken());
        XxlJobHelper.log("fenced stale outbox recovery owner={} delivered={}", request.owner(), delivered);
    }

    @XxlJob("manualOutboxRecoveryJob")
    public void recoverManually() {
        RecoveryRequest request = request(false);
        int delivered = backendJobClient.recoverOutboxManually(request.owner(), request.operationEpoch(), request.fenceToken());
        XxlJobHelper.log("fenced manual outbox recovery owner={} delivered={}", request.owner(), delivered);
    }

    @XxlJob("fencedOutboxTakeoverJob")
    public void takeover() {
        RecoveryRequest request = request(false);
        int delivered = backendJobClient.fencedTakeover(request.owner(), request.operationEpoch(), request.fenceToken());
        XxlJobHelper.log("fenced outbox takeover owner={} epoch={} delivered={}",
                request.owner(), request.operationEpoch(), delivered);
    }

    private RecoveryRequest request(boolean eventIdRequired) {
        String parameter = XxlJobHelper.getJobParam();
        if (parameter == null || parameter.isBlank()) {
            throw new IllegalArgumentException("Recovery job parameter JSON is required");
        }
        try {
            RecoveryRequest request = objectMapper.readValue(parameter, RecoveryRequest.class);
            if (request.owner() == null || request.owner().isBlank()) throw new IllegalArgumentException("owner is required");
            if (request.operationEpoch() <= 0L) throw new IllegalArgumentException("operationEpoch must be positive");
            if (request.fenceToken() == null || request.fenceToken().length() < 24) {
                throw new IllegalArgumentException("fenceToken must contain at least 24 characters");
            }
            if (eventIdRequired && (request.eventId() == null || request.eventId() <= 0L)) {
                throw new IllegalArgumentException("eventId must be positive");
            }
            return request;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Recovery job parameter must be valid JSON", exception);
        }
    }

    record RecoveryRequest(String owner, Long eventId, long operationEpoch, String fenceToken) { }
}
