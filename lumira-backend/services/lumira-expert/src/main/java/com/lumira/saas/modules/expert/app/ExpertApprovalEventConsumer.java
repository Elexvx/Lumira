package com.lumira.saas.modules.expert.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventConsumptionPort;
import com.lumira.api.expert.ExpertAccountProvisioningPort;
import com.lumira.api.expert.ExpertApprovalEventHandler;
import com.lumira.api.workflow.WorkflowEventTypes;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedUserSnapshotResolver;
import com.lumira.saas.modules.expert.repository.ExpertApprovalRepository;
import com.lumira.saas.modules.expert.repository.ExpertApprovalRepository.ExpertAccountRecord;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Expert-owned provisioning workflow for durable expert-approval events. */
@Component
@ConditionalOnLumiraControlPlaneEnabled
public class ExpertApprovalEventConsumer implements ExpertApprovalEventHandler {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String REQUIRED_PERMISSION_CREATE_USER = "system:user:create";
    private static final String SOURCE_SYSTEM = "SYSTEM";
    private static final String CONSUMER_NAME = "expert-approval-v1";

    private final ExpertApprovalRepository approvalRepository;
    private final ObjectMapper objectMapper;
    private final TrustedUserSnapshotResolver trustedUserSnapshotResolver;
    private final ExpertAccountProvisioningPort accountProvisioningPort;
    private final EventConsumptionPort eventConsumptionPort;

    public ExpertApprovalEventConsumer(
            ExpertApprovalRepository approvalRepository,
            ObjectMapper objectMapper,
            TrustedUserSnapshotResolver trustedUserSnapshotResolver,
            ExpertAccountProvisioningPort accountProvisioningPort,
            EventConsumptionPort eventConsumptionPort
    ) {
        this.approvalRepository = approvalRepository;
        this.objectMapper = objectMapper;
        this.trustedUserSnapshotResolver = trustedUserSnapshotResolver;
        this.accountProvisioningPort = accountProvisioningPort;
        this.eventConsumptionPort = eventConsumptionPort;
    }

    @Override
    @Transactional
    public void handle(ExpertApprovalEvent event) {
        requireTrustedExpertApprovedEvent(event);
        Long expertId = aggregateId(event);
        if (expertId == null) {
            throw new IllegalStateException("EXPERT_APPROVED event missing aggregateId");
        }
        requireEventKeyMatchesExpert(event, expertId);
        eventConsumptionPort.executeOnce(
                new EventConsumptionPort.EventIdentity(
                        CONSUMER_NAME,
                        String.valueOf(event.eventId()),
                        event.eventType(),
                        "workflow",
                        String.valueOf(expertId)
                ),
                () -> provisionApprovedExpert(event, expertId)
        );
    }

    private void provisionApprovedExpert(ExpertApprovalEvent event, Long expertId) {
        Long workflowInstanceId = requirePayloadWorkflowInstanceId(event);
        String businessUuid = requirePayloadBusinessUuid(event);
        CurrentUser operator = buildOperator(event);
        ExpertAccountRecord expert = loadExpert(expertId, workflowInstanceId, businessUuid);
        if (expert == null) {
            throw new IllegalStateException("Expert not found: " + expertId);
        }
        if (!StringUtils.hasText(expert.email())) {
            throw new IllegalStateException("Expert email is required before account activation");
        }

        ExpertAccountProvisioningPort.AccountIdentity account;
        boolean accountCreated = false;
        if (expert.userId() == null) {
            account = loadExistingApplicant(expert);
            if (account != null) {
                int linked = approvalRepository.bindExistingAccount(
                        expertId,
                        businessUuid,
                        workflowInstanceId,
                        account.userId(),
                        account.userUuid(),
                        operator.getUserId(),
                        operator.getUserUuid(),
                        LocalDateTime.now()
                );
                if (linked != 1) {
                    throw new IllegalStateException("Expert applicant binding changed before role assignment");
                }
            } else {
                String username = nextUsername(expert.code(), expertId);
                account = requireAccount(accountProvisioningPort.createExpertAccount(
                        operator,
                        new ExpertAccountProvisioningPort.CreateExpertAccount(
                                username,
                                randomPassword(),
                                expert.mobile(),
                                expert.email(),
                                expert.name()
                        )
                ), "Created expert account missing user uuid");
                int linked = approvalRepository.bindAccount(
                        expertId,
                        businessUuid,
                        workflowInstanceId,
                        account.userId(),
                        account.userUuid(),
                        operator.getUserId(),
                        operator.getUserUuid(),
                        LocalDateTime.now()
                );
                if (linked != 1) {
                    throw new IllegalStateException("Expert account binding changed before activation");
                }
                accountCreated = true;
            }
        } else {
            account = requireExistingExpertAccount(expert);
        }

        accountProvisioningPort.ensureExpertRole(operator, account);
        if (accountCreated) {
            accountProvisioningPort.sendActivation(operator, expertId, expert.email(), account);
        }
    }

    private void requireTrustedExpertApprovedEvent(ExpertApprovalEvent event) {
        if (event == null || event.eventId() == null || event.eventId() <= 0) {
            throw new IllegalStateException("EXPERT_APPROVED event id is required");
        }
        if (!SOURCE_SYSTEM.equals(event.sourceType())) {
            throw new IllegalStateException("EXPERT_APPROVED event sourceType must be SYSTEM");
        }
        if (!WorkflowEventTypes.EXPERT_APPROVED.equals(event.eventType())) {
            throw new IllegalStateException("EXPERT_APPROVED event type mismatch");
        }
        if (event.payloadJson() != null && event.payloadJson().length() > 64 * 1024) {
            throw new IllegalStateException("EXPERT_APPROVED event payload is too large");
        }
    }

    private CurrentUser buildOperator(ExpertApprovalEvent event) {
        if (event.userId() == null || event.userId() <= 0) {
            throw new IllegalStateException("EXPERT_APPROVED event missing trusted operator");
        }
        String eventUserUuid = requireEventUserUuid(event);
        String expectedUserUuid = requirePayloadUserUuid(event);
        Long simulatedRoleId = requirePayloadSimulatedRoleId(event);
        if (!eventUserUuid.equals(expectedUserUuid)) {
            throw new IllegalStateException("EXPERT_APPROVED event operator uuid mismatch");
        }
        CurrentUser operator = trustedUserSnapshotResolver.resolve(
                event.userId(),
                eventUserUuid,
                simulatedRoleId,
                "internal-workflow-event-" + event.eventId(),
                REQUIRED_PERMISSION_CREATE_USER
        );
        if (operator == null || operator.getUserId() == null || !event.userId().equals(operator.getUserId())
                || !eventUserUuid.equals(trim(operator.getUserUuid()))) {
            throw new IllegalStateException("EXPERT_APPROVED event operator is not trusted");
        }
        return operator;
    }

    private ExpertAccountProvisioningPort.AccountIdentity loadExistingApplicant(ExpertAccountRecord expert) {
        if (expert == null || expert.createdBy() == null || expert.createdBy() <= 0
                || !StringUtils.hasText(expert.createdByUuid())) {
            return null;
        }
        ExpertAccountProvisioningPort.AccountIdentity applicant = accountProvisioningPort.findAccount(expert.createdBy());
        if (applicant == null) {
            return null;
        }
        if (!expert.createdByUuid().trim().equals(trim(applicant.userUuid()))) {
            throw new IllegalStateException("Expert applicant uuid mismatch");
        }
        return requireAccount(applicant, "Expert applicant account is invalid");
    }

    private ExpertAccountProvisioningPort.AccountIdentity requireExistingExpertAccount(ExpertAccountRecord expert) {
        if (expert.userId() == null || !StringUtils.hasText(expert.userUuid())) {
            throw new IllegalStateException("Expert account binding missing user uuid");
        }
        ExpertAccountProvisioningPort.AccountIdentity account = accountProvisioningPort.findAccount(expert.userId());
        account = requireAccount(account, "Expert account binding user does not exist");
        if (!expert.userUuid().trim().equals(account.userUuid())) {
            throw new IllegalStateException("Expert account binding missing user uuid");
        }
        return account;
    }

    private ExpertAccountProvisioningPort.AccountIdentity requireAccount(
            ExpertAccountProvisioningPort.AccountIdentity account,
            String message
    ) {
        if (account == null || account.userId() == null || account.userId() <= 0
                || !StringUtils.hasText(account.userUuid()) || !StringUtils.hasText(account.username())) {
            throw new IllegalStateException(message);
        }
        return new ExpertAccountProvisioningPort.AccountIdentity(
                account.userId(), account.userUuid().trim(), account.username().trim()
        );
    }

    private ExpertAccountRecord loadExpert(Long expertId, Long workflowInstanceId, String businessUuid) {
        return approvalRepository.findApprovedExpert(expertId, businessUuid, workflowInstanceId).orElse(null);
    }

    private void requireEventKeyMatchesExpert(ExpertApprovalEvent event, Long expertId) {
        if (expertId == null || !StringUtils.hasText(event.eventKey())) {
            throw new IllegalStateException("EXPERT_APPROVED event key is required");
        }
        String expected = WorkflowEventTypes.EXPERT_APPROVED + ":aiadc_expert:" + expertId;
        if (!expected.equals(event.eventKey().trim())) {
            throw new IllegalStateException("EXPERT_APPROVED event key mismatch");
        }
    }

    private String requireEventUserUuid(ExpertApprovalEvent event) {
        if (!StringUtils.hasText(event.userUuid())) {
            throw new IllegalStateException("EXPERT_APPROVED event missing operator uuid");
        }
        return event.userUuid().trim();
    }

    private String nextUsername(String code, Long expertId) {
        String base = "expert_" + (StringUtils.hasText(code) ? code : "approved_" + expertId)
                .replaceAll("[^A-Za-z0-9_-]", "_");
        return accountProvisioningPort.usernameExists(base) ? base + "_" + expertId : base;
    }

    private String randomPassword() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return "Ex" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "Aa1!";
    }

    private Long aggregateId(ExpertApprovalEvent event) {
        try {
            Object aggregateId = payload(event).get("aggregateId");
            if (aggregateId instanceof Number number) {
                return positiveAggregateId(number.longValue());
            }
            if (aggregateId != null) {
                return positiveAggregateId(Long.parseLong(String.valueOf(aggregateId)));
            }
        } catch (RuntimeException ignored) {
            // Fall back to the immutable event-key format.
        }
        if (!StringUtils.hasText(event.eventKey())) {
            return null;
        }
        String[] parts = event.eventKey().split(":");
        try {
            return positiveAggregateId(Long.parseLong(parts[parts.length - 1].trim().toLowerCase(Locale.ROOT)));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String requirePayloadUserUuid(ExpertApprovalEvent event) {
        Object value = payloadValue(event, "userUuid");
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        throw new IllegalStateException("EXPERT_APPROVED event missing operator uuid");
    }

    private Long requirePayloadWorkflowInstanceId(ExpertApprovalEvent event) {
        Long workflowInstanceId = coercePositiveLong(payloadValue(event, "workflowInstanceId"));
        if (workflowInstanceId == null) {
            throw new IllegalStateException("EXPERT_APPROVED event missing workflow instance id");
        }
        return workflowInstanceId;
    }

    private String requirePayloadBusinessUuid(ExpertApprovalEvent event) {
        Object value = payloadValue(event, "businessUuid");
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        throw new IllegalStateException("EXPERT_APPROVED event missing business uuid");
    }

    private Long requirePayloadSimulatedRoleId(ExpertApprovalEvent event) {
        return coercePositiveLong(payloadValue(event, "simulatedRoleId"));
    }

    private Object payloadValue(ExpertApprovalEvent event, String key) {
        Map<String, Object> payload = payload(event);
        Object direct = payload.get(key);
        if (direct != null) {
            return direct;
        }
        Object attributes = payload.get("attributes");
        return attributes instanceof Map<?, ?> map ? map.get(key) : null;
    }

    private Map<String, Object> payload(ExpertApprovalEvent event) {
        try {
            if (event == null || !StringUtils.hasText(event.payloadJson())) {
                return Map.of();
            }
            return objectMapper.readValue(event.payloadJson(), new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("EXPERT_APPROVED event payload is invalid", exception);
        }
    }

    private Long positiveAggregateId(Long aggregateId) {
        return aggregateId == null || aggregateId <= 0 ? null : aggregateId;
    }

    private Long coercePositiveLong(Object value) {
        try {
            if (value instanceof Number number) {
                return positiveAggregateId(number.longValue());
            }
            return value == null ? null : positiveAggregateId(Long.parseLong(String.valueOf(value)));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
