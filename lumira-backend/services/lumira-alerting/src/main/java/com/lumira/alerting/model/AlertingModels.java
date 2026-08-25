package com.lumira.alerting.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AlertingModels {
    private AlertingModels() {
    }

    public record CatalogSignal(
            String key,
            String name,
            String sourceType,
            String unit,
            List<String> comparators,
            String description
    ) {
    }

    public record ChannelRequest(
            @NotBlank @Size(max = 128) String name,
            @NotBlank String type,
            boolean enabled,
            @NotNull Map<String, Object> config,
            Long version
    ) {
    }

    public record ChannelView(
            Long id,
            String name,
            String type,
            boolean enabled,
            Map<String, Object> config,
            boolean secretConfigured,
            String lastTestStatus,
            String lastTestError,
            LocalDateTime lastTestAt,
            long version,
            LocalDateTime updatedAt
    ) {
    }

    public record ContactMemberRequest(
            @NotNull Long channelId,
            @NotBlank String memberType,
            @NotBlank @Size(max = 512) String targetIdentifier,
            @Size(max = 128) String displayName,
            boolean enabled
    ) {
    }

    public record ContactGroupRequest(
            @NotBlank @Size(max = 128) String name,
            boolean enabled,
            @NotNull List<ContactMemberRequest> members,
            Long version
    ) {
    }

    public record ContactMemberView(
            Long id,
            Long channelId,
            String channelName,
            String channelType,
            String memberType,
            String targetIdentifier,
            String displayName,
            boolean enabled
    ) {
    }

    public record ContactGroupView(
            Long id,
            String name,
            boolean enabled,
            List<ContactMemberView> members,
            long version,
            LocalDateTime updatedAt
    ) {
    }

    public record RuleRequest(
            @NotBlank @Size(max = 128) String name,
            @NotBlank String sourceType,
            @NotBlank String signalKey,
            @NotBlank String comparator,
            @NotNull BigDecimal threshold,
            @DecimalMin("1") Integer windowSeconds,
            @DecimalMin("0") Integer pendingSeconds,
            @NotBlank String severity,
            @NotNull Long contactGroupId,
            boolean enabled,
            Map<String, String> labels,
            Long version
    ) {
    }

    public record RuleView(
            Long id,
            String name,
            String sourceType,
            String signalKey,
            String comparator,
            BigDecimal threshold,
            int windowSeconds,
            int pendingSeconds,
            String severity,
            Long contactGroupId,
            String contactGroupName,
            boolean enabled,
            Map<String, String> labels,
            String evaluationError,
            LocalDateTime lastEvaluatedAt,
            long version,
            LocalDateTime updatedAt
    ) {
    }

    public record AlertInstanceView(
            Long id,
            Long ruleId,
            String ruleName,
            String severity,
            String status,
            BigDecimal lastValue,
            LocalDateTime startedAt,
            LocalDateTime firingAt,
            LocalDateTime resolvedAt,
            LocalDateTime acknowledgedAt,
            Long acknowledgedBy,
            String evaluationError,
            long version
    ) {
    }

    public record SilenceRequest(
            @NotBlank @Size(max = 128) String name,
            Long ruleId,
            @NotNull LocalDateTime startsAt,
            @NotNull LocalDateTime endsAt,
            @NotBlank @Size(max = 500) String reason,
            boolean enabled,
            Long version
    ) {
    }

    public record SilenceView(
            Long id,
            String name,
            Long ruleId,
            String ruleName,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String reason,
            boolean enabled,
            long version,
            LocalDateTime updatedAt
    ) {
    }

    public record DeliveryView(
            Long id,
            Long instanceId,
            String eventType,
            String channelName,
            String channelType,
            String recipient,
            String status,
            int attempts,
            String lastError,
            LocalDateTime nextAttemptAt,
            LocalDateTime sentAt,
            LocalDateTime createdAt
    ) {
    }

    public record DirectoryMappingRequest(
            @NotNull Long channelId,
            @NotNull Long userId,
            @NotBlank String userUuid,
            @NotBlank String providerUserId,
            @Size(max = 128) String providerDisplayName
    ) {
    }

    public record DirectoryMappingView(
            Long id,
            Long channelId,
            Long userId,
            String userUuid,
            String providerUserId,
            String providerDisplayName,
            String matchSource,
            String status,
            boolean manualOverride,
            LocalDateTime syncedAt
    ) {
    }

    public record HealthView(
            boolean pluginEnabled,
            String workerStatus,
            LocalDateTime workerHeartbeatAt,
            long enabledRules,
            long firingAlerts,
            long pendingDeliveries,
            long deadLetters,
            String lastEvaluationError
    ) {
    }

    public record JobRunResult(
            boolean pluginEnabled,
            int evaluatedRules,
            int claimedDeliveries,
            int sentDeliveries,
            int failedDeliveries
    ) {
    }
}
