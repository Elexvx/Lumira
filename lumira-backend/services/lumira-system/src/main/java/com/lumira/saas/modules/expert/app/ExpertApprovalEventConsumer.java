package com.lumira.saas.modules.expert.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.event.PlatformEventConsumer;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxEntity;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import com.lumira.saas.modules.expert.repository.ExpertApprovalRepository;
import com.lumira.saas.modules.expert.repository.ExpertApprovalRepository.ExpertAccountRecord;
import com.lumira.saas.modules.expert.repository.ExpertApprovalRepository.OperatorRecord;
import com.lumira.saas.modules.account.app.AccountActivationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnLumiraControlPlaneEnabled
public class ExpertApprovalEventConsumer implements PlatformEventConsumer {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int WORKFLOW_OPERATOR_SESSION_VERSION = 1;
    private static final String REQUIRED_PERMISSION_CREATE_USER = "system:user:create";

    private final ExpertApprovalRepository approvalRepository;
    private final ObjectMapper objectMapper;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemUserManagementAppService systemUserManagementAppService;
    private final AccountActivationService accountActivationService;

    public ExpertApprovalEventConsumer(
            ExpertApprovalRepository approvalRepository,
            ObjectMapper objectMapper,
            PermissionSnapshotService permissionSnapshotService,
            SystemUserManagementAppService systemUserManagementAppService,
            AccountActivationService accountActivationService
    ) {
        this.approvalRepository = approvalRepository;
        this.objectMapper = objectMapper;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemUserManagementAppService = systemUserManagementAppService;
        this.accountActivationService = accountActivationService;
    }

    @Override
    public boolean supports(PlatformEventOutboxEntity event) {
        return event != null && WorkflowAppService.EVENT_EXPERT_APPROVED.equals(event.getEventType());
    }

    @Override
    @Transactional
    public void consume(PlatformEventOutboxEntity event) {
        requireTrustedExpertApprovedEvent(event);
        Long expertId = aggregateId(event);
        if (expertId == null) {
            throw new IllegalStateException("EXPERT_APPROVED event missing aggregateId");
        }
        requireEventKeyMatchesExpert(event, expertId);
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
        Long userId = expert.userId();
        String userUuid = expert.userUuid();
        String username = expert.username();
        boolean accountCreated = false;
        if (userId == null) {
            OperatorRecord applicant = loadExistingApplicant(expert);
            if (applicant != null) {
                userId = applicant.userId();
                userUuid = applicant.userUuid();
                username = applicant.username();
                int linked = approvalRepository.bindExistingAccount(
                        expertId,
                        businessUuid,
                        workflowInstanceId,
                        userId,
                        userUuid,
                        operator.getUserId(),
                        operator.getUserUuid(),
                        LocalDateTime.now()
                );
                if (linked != 1) {
                    throw new IllegalStateException("Expert applicant binding changed before role assignment");
                }
            } else {
                username = nextUsername(expert.code(), expertId);
                SystemDTO.UserUpsertRequest userRequest = new SystemDTO.UserUpsertRequest();
                userRequest.setUsername(username);
                userRequest.setPassword(randomPassword());
                userRequest.setMobile(expert.mobile());
                userRequest.setEmail(expert.email());
                userRequest.setRealName(expert.name());
                userRequest.setNickname(expert.name());
                userRequest.setStatus("ENABLED");
                Long expertRoleId = findRoleId("EXPERT");
                userRequest.setRoleIds(expertRoleId == null ? List.of() : List.of(expertRoleId));
                SystemVO.UserDetailVO createdUser = systemUserManagementAppService.createUserFromTrustedSnapshot(operator, userRequest);
                userId = createdUser.getId();
                userUuid = requireCreatedUserUuid(createdUser);
                int linked = approvalRepository.bindAccount(expertId, businessUuid, workflowInstanceId, userId, userUuid,
                        operator.getUserId(), operator.getUserUuid(), LocalDateTime.now());
                if (linked != 1) {
                    throw new IllegalStateException("Expert account binding changed before activation");
                }
                accountCreated = true;
            }
        } else {
            requireExistingExpertUser(userId, userUuid, username);
        }
        Long expertRoleId = findRoleId("EXPERT");
        if (expertRoleId == null) {
            throw new IllegalStateException("Expert role is not configured");
        }
        approvalRepository.ensureRoleAssignment(
                userId,
                userUuid,
                expertRoleId,
                operator.getUserId(),
                operator.getUserUuid()
        );
        if (accountCreated) {
            String token = accountActivationService.createActivationToken(userId, expertId, operator.getUserId(), operator.getUserUuid());
            accountActivationService.sendActivationEmail(expert.email(), username, token);
        }
    }

    private void requireTrustedExpertApprovedEvent(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null || event.getId() <= 0) {
            throw new IllegalStateException("EXPERT_APPROVED event id is required");
        }
        if (!PlatformEventTypes.SOURCE_SYSTEM.equals(event.getSourceType())) {
            throw new IllegalStateException("EXPERT_APPROVED event sourceType must be SYSTEM");
        }
        if (!WorkflowAppService.EVENT_EXPERT_APPROVED.equals(event.getEventType())) {
            throw new IllegalStateException("EXPERT_APPROVED event type mismatch");
        }
        if (event.getPayloadJson() != null && event.getPayloadJson().length() > 64 * 1024) {
            throw new IllegalStateException("EXPERT_APPROVED event payload is too large");
        }
    }

    private CurrentUser buildOperator(PlatformEventOutboxEntity event) {
        if (event == null || event.getUserId() == null || event.getUserId() <= 0) {
            throw new IllegalStateException("EXPERT_APPROVED event missing trusted operator");
        }
        String eventUserUuid = requireEventUserUuid(event);
        String expectedUserUuid = requirePayloadUserUuid(event);
        Long simulatedRoleId = requirePayloadSimulatedRoleId(event);
        if (!eventUserUuid.equals(expectedUserUuid)) {
            throw new IllegalStateException("EXPERT_APPROVED event operator uuid mismatch");
        }
        OperatorRecord operatorRow = loadTrustedOperator(event.getUserId());
        if (!expectedUserUuid.equals(operatorRow.userUuid())) {
            throw new IllegalStateException("EXPERT_APPROVED event operator uuid mismatch");
        }
        PermissionSnapshotService.PermissionSnapshot permissionSnapshot =
                simulatedRoleId != null
                        ? permissionSnapshotService.loadGrantedRoleSnapshot(
                        operatorRow.userId(),
                        operatorRow.userUuid(),
                        simulatedRoleId
                )
                        : permissionSnapshotService.loadSnapshot(operatorRow.userId(), operatorRow.userUuid());
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.getVersion())) {
            throw new IllegalStateException("EXPERT_APPROVED event operator permission snapshot is unavailable");
        }
        Set<String> permissions = permissionSnapshot.getPermissions() == null
                ? Set.of()
                : Set.copyOf(permissionSnapshot.getPermissions());
        if (!permissions.contains(REQUIRED_PERMISSION_CREATE_USER)) {
            throw new IllegalStateException("EXPERT_APPROVED event operator lacks required permission");
        }
        CurrentUser operator = new CurrentUser();
        operator.setUserId(operatorRow.userId());
        operator.setUserUuid(operatorRow.userUuid());
        operator.setUsername(operatorRow.username());
        operator.setSessionId(workflowOperatorSessionId(event));
        operator.setSessionVersion(WORKFLOW_OPERATOR_SESSION_VERSION);
        operator.setPermissionsVersion(permissionSnapshot.getVersion());
        operator.setAuthenticated(true);
        operator.setPermissions(permissions);
        operator.setRoleIds(permissionSnapshot.getRoleIds());
        operator.setPrimaryDeptId(permissionSnapshot.getPrimaryDeptId());
        operator.setDeptIds(permissionSnapshot.getDeptIds());
        operator.setDescendantDeptIds(permissionSnapshot.getDescendantDeptIds());
        operator.setDataScopes(permissionSnapshot.getDataScopes());
        operator.setDefaultHomePath(permissionSnapshot.getDefaultHomePath());
        operator.setSimulatedRoleId(simulatedRoleId);
        return operator;
    }

    private OperatorRecord loadTrustedOperator(Long userId) {
        OperatorRecord operator = approvalRepository.findOperator(userId).orElse(null);
        if (operator == null) {
            throw new IllegalStateException("EXPERT_APPROVED event operator does not exist");
        }
        if (!StringUtils.hasText(operator.username())) {
            throw new IllegalStateException("EXPERT_APPROVED event operator username is required");
        }
        if (!StringUtils.hasText(operator.userUuid())) {
            throw new IllegalStateException("EXPERT_APPROVED event operator uuid is required");
        }
        if (!StringUtils.hasText(operator.status()) || !"ENABLED".equalsIgnoreCase(operator.status().trim())) {
            throw new IllegalStateException("EXPERT_APPROVED event operator is disabled");
        }
        return new OperatorRecord(operator.userId(), operator.userUuid().trim(), operator.username().trim(), operator.status());
    }

    private OperatorRecord loadExistingApplicant(ExpertAccountRecord expert) {
        if (expert == null || expert.createdBy() == null || expert.createdBy() <= 0
                || !StringUtils.hasText(expert.createdByUuid())) {
            return null;
        }
        OperatorRecord applicant = approvalRepository.findOperator(expert.createdBy()).orElse(null);
        if (applicant == null) {
            return null;
        }
        String expectedUuid = expert.createdByUuid().trim();
        if (!expectedUuid.equals(applicant.userUuid())) {
            throw new IllegalStateException("Expert applicant uuid mismatch");
        }
        return applicant;
    }

    private String workflowOperatorSessionId(PlatformEventOutboxEntity event) {
        return "internal-workflow-event-" + event.getId();
    }
    private ExpertAccountRecord loadExpert(Long expertId, Long workflowInstanceId, String businessUuid) {
        return approvalRepository.findApprovedExpert(expertId, businessUuid, workflowInstanceId).orElse(null);
    }

    private void requireEventKeyMatchesExpert(PlatformEventOutboxEntity event, Long expertId) {
        if (event == null || expertId == null || !StringUtils.hasText(event.getEventKey())) {
            throw new IllegalStateException("EXPERT_APPROVED event key is required");
        }
        String expected = WorkflowAppService.EVENT_EXPERT_APPROVED + ":aiadc_expert:" + expertId;
        if (!expected.equals(event.getEventKey().trim())) {
            throw new IllegalStateException("EXPERT_APPROVED event key mismatch");
        }
    }

    private String requireEventUserUuid(PlatformEventOutboxEntity event) {
        if (event == null || !StringUtils.hasText(event.getUserUuid())) {
            throw new IllegalStateException("EXPERT_APPROVED event missing operator uuid");
        }
        return event.getUserUuid().trim();
    }

    private String requireCreatedUserUuid(SystemVO.UserDetailVO createdUser) {
        if (createdUser == null || createdUser.getId() == null || !StringUtils.hasText(createdUser.getUserUuid())) {
            throw new IllegalStateException("Created expert account missing user uuid");
        }
        return createdUser.getUserUuid().trim();
    }

    private void requireExistingExpertUser(Long userId, String userUuid, String username) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            throw new IllegalStateException("Expert account binding missing user uuid");
        }
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("Expert account binding user does not exist");
        }
    }

    private Long findRoleId(String roleCode) {
        return approvalRepository.findRoleId(roleCode).orElse(null);
    }

    private String nextUsername(String code, Long expertId) {
        String base = "expert_" + (StringUtils.hasText(code) ? code : "approved_" + expertId).replaceAll("[^A-Za-z0-9_-]", "_");
        if (!usernameExists(base)) {
            return base;
        }
        return base + "_" + expertId;
    }

    private boolean usernameExists(String username) {
        return approvalRepository.usernameExists(username);
    }

    private String randomPassword() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return "Ex" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "Aa1!";
    }

    private Long aggregateId(PlatformEventOutboxEntity event) {
        try {
            Map<String, Object> payload = payload(event);
            Object aggregateId = payload.get("aggregateId");
            if (aggregateId instanceof Number number) {
                return positiveAggregateId(number.longValue());
            }
            if (aggregateId != null) {
                return positiveAggregateId(Long.parseLong(String.valueOf(aggregateId)));
            }
        } catch (Exception ignored) {
            // Fall back to event key parsing.
        }
        if (!StringUtils.hasText(event.getEventKey())) {
            return null;
        }
        String[] parts = event.getEventKey().split(":");
        if (parts.length == 0) {
            return null;
        }
        try {
            return positiveAggregateId(Long.parseLong(parts[parts.length - 1].trim().toLowerCase(Locale.ROOT)));
        } catch (Exception exception) {
            return null;
        }
    }

    private String requirePayloadUserUuid(PlatformEventOutboxEntity event) {
        Map<String, Object> payload = payload(event);
        Object value = payload.get("userUuid");
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        Object attributes = payload.get("attributes");
        if (attributes instanceof Map<?, ?> map) {
            Object nested = map.get("userUuid");
            if (nested instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        throw new IllegalStateException("EXPERT_APPROVED event missing operator uuid");
    }

    private Long requirePayloadWorkflowInstanceId(PlatformEventOutboxEntity event) {
        Map<String, Object> payload = payload(event);
        Object value = payload.get("workflowInstanceId");
        Long workflowInstanceId = coercePositiveLong(value);
        if (workflowInstanceId != null) {
            return workflowInstanceId;
        }
        Object attributes = payload.get("attributes");
        if (attributes instanceof Map<?, ?> map) {
            workflowInstanceId = coercePositiveLong(map.get("workflowInstanceId"));
            if (workflowInstanceId != null) {
                return workflowInstanceId;
            }
        }
        throw new IllegalStateException("EXPERT_APPROVED event missing workflow instance id");
    }

    private String requirePayloadBusinessUuid(PlatformEventOutboxEntity event) {
        Map<String, Object> payload = payload(event);
        Object value = payload.get("businessUuid");
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        Object attributes = payload.get("attributes");
        if (attributes instanceof Map<?, ?> map) {
            Object nested = map.get("businessUuid");
            if (nested instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        throw new IllegalStateException("EXPERT_APPROVED event missing business uuid");
    }

    private Long requirePayloadSimulatedRoleId(PlatformEventOutboxEntity event) {
        Map<String, Object> payload = payload(event);
        Long simulatedRoleId = coercePositiveLong(payload.get("simulatedRoleId"));
        if (simulatedRoleId != null) {
            return simulatedRoleId;
        }
        Object attributes = payload.get("attributes");
        if (attributes instanceof Map<?, ?> map) {
            return coercePositiveLong(map.get("simulatedRoleId"));
        }
        return null;
    }

    private Map<String, Object> payload(PlatformEventOutboxEntity event) {
        try {
            if (event == null || !StringUtils.hasText(event.getPayloadJson())) {
                return Map.of();
            }
            return objectMapper.readValue(event.getPayloadJson(), new TypeReference<>() {});
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
            if (value != null) {
                return positiveAggregateId(Long.parseLong(String.valueOf(value)));
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

}
