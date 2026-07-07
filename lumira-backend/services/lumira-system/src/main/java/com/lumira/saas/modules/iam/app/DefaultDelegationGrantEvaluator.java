package com.lumira.saas.modules.iam.app;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationVerdict;
import com.lumira.common.security.authorization.DelegationGrantDecision;
import com.lumira.common.security.authorization.DelegationGrantEvaluator;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultDelegationGrantEvaluator implements DelegationGrantEvaluator {

    private final MyBatisQueryOperations jdbcTemplate;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public DefaultDelegationGrantEvaluator(MyBatisQueryOperations jdbcTemplate) {
        this(jdbcTemplate, null, false);
    }

    @Autowired
    public DefaultDelegationGrantEvaluator(
            MyBatisQueryOperations jdbcTemplate,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, sessionAuthenticationService, true);
    }

    private DefaultDelegationGrantEvaluator(
            MyBatisQueryOperations jdbcTemplate,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @Override
    public DelegationGrantDecision evaluate(AuthorizationRequest request) {
        if (request == null || (!"AI_AGENT".equals(normalize(request.channel())) && request.employeeId() == null)) {
            return DelegationGrantDecision.notInScope();
        }
        Long trustedCurrentUserId = trustedUserIdOrNull(request.currentUser());
        String trustedCurrentUserUuid = trustedUserUuidOrNull(request.currentUser());
        Long humanUserId = request.humanUserId() == null ? trustedCurrentUserId : request.humanUserId();
        String humanUserUuid = StringUtils.hasText(request.humanUserUuid())
                ? request.humanUserUuid().trim()
                : trustedCurrentUserUuid;
        Long employeeId = request.employeeId();
        if (humanUserId == null || humanUserId <= 0 || !StringUtils.hasText(humanUserUuid)
                || employeeId == null || employeeId <= 0) {
            return DelegationGrantDecision.deny("DELEGATION_CONTEXT_INCOMPLETE", "Delegation subject context is incomplete");
        }
        if (trustedCurrentUserId == null || !humanUserId.equals(trustedCurrentUserId)
                || !humanUserUuid.equals(trustedCurrentUserUuid)) {
            return DelegationGrantDecision.deny("DELEGATION_HUMAN_SUBJECT_UNTRUSTED", "Delegating human subject is not trusted");
        }
        Long humanSubjectId = subjectId("HUMAN_USER", humanUserId);
        if (humanSubjectId == null) {
            return DelegationGrantDecision.deny("DELEGATION_HUMAN_SUBJECT_NOT_FOUND", "Delegating human subject was not found");
        }
        Long employeeSubjectId = subjectId("DIGITAL_EMPLOYEE", employeeId);
        if (employeeSubjectId == null) {
            return DelegationGrantDecision.deny("DELEGATION_AGENT_SUBJECT_NOT_FOUND", "Delegated digital employee subject was not found");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select id, resource_code as resourceCode, action_code as actionCode,
                               permission_key as permissionKey, tool_code as toolCode,
                               scope_type as scopeType, max_risk_level as maxRiskLevel,
                               require_confirm as requireConfirm, require_approval as requireApproval
                        from iam_delegation_grant
                        where delegator_subject_id = ?
                          and delegate_subject_id = ?
                          and status = 'ENABLED'
                          and deleted = 0
                          and (valid_from is null or valid_from <= current_timestamp)
                          and (expires_at is null or expires_at > current_timestamp)
                          and (tool_code is null or tool_code = ?)
                          and (permission_key is null or permission_key = ? or permission_key = '*')
                          and (resource_code is null or resource_code = ?)
                          and (action_code is null or action_code = ?)
                        """,
                humanSubjectId,
                employeeSubjectId,
                trim(request.toolCode()),
                trim(request.permissionKey()),
                trim(request.resourceCode()),
                trim(request.actionCode())
        );
        Map<String, Object> grant = rows.stream()
                .max(Comparator.comparingInt(row -> specificity(row, request)))
                .orElse(null);
        if (grant == null) {
            return DelegationGrantDecision.deny("DELEGATION_GRANT_NOT_FOUND", "Delegation grant was not found");
        }
        String scopeType = stringValue(grant.get("scopeType"), "SELF");
        String maxRiskLevel = stringValue(grant.get("maxRiskLevel"), "LOW");
        boolean requireConfirm = toBoolean(grant.get("requireConfirm"));
        boolean requireApproval = toBoolean(grant.get("requireApproval"));
        List<String> matched = List.of("DELEGATION_GRANT_MATCH", "DELEGATION_GRANT_" + scopeType.toUpperCase(Locale.ROOT));
        if (requireApproval && !request.approvalGranted()) {
            return DelegationGrantDecision.requireApproval("DELEGATION_APPROVAL_REQUIRED", "Delegation approval is required", matched);
        }
        if (requireConfirm && !request.confirmed()) {
            return DelegationGrantDecision.requireConfirm("DELEGATION_CONFIRM_REQUIRED", "Delegation confirmation is required", matched);
        }
        return DelegationGrantDecision.allow(scopeType, maxRiskLevel, requireConfirm, requireApproval, matched);
    }

    private Long subjectId(String subjectType, Long refId) {
        return jdbcTemplate.queryForObject(
                """
                        select id
                        from iam_subject
                        where subject_type = ?
                          and ref_id = ?
                          and status = 'ENABLED'
                          and deleted = 0
                        limit 1
                """,
                Long.class,
                subjectType,
                refId
        );
    }

    private Long trustedUserIdOrNull(CurrentUser currentUser) {
        CurrentUser trustedCurrentUser = trustedCurrentUserOrNull(currentUser);
        return trustedCurrentUser == null ? null : trustedCurrentUser.getUserId();
    }

    private String trustedUserUuidOrNull(CurrentUser currentUser) {
        CurrentUser trustedCurrentUser = trustedCurrentUserOrNull(currentUser);
        if (trustedCurrentUser == null) {
            return null;
        }
        return trustedCurrentUser.getUserUuid().trim();
    }

    private CurrentUser trustedCurrentUserOrNull(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return null;
        }
        if (sessionAuthenticationService == null) {
            if (enforceTrustedUserResolution) {
                return null;
            }
            return currentUser;
        }
        try {
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess =
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    );
            CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
            return AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser) ? refreshedUser : null;
        } catch (BizException exception) {
            return null;
        }
    }

    private int specificity(Map<String, Object> row, AuthorizationRequest request) {
        int score = 0;
        if (matches(row.get("toolCode"), request.toolCode())) {
            score += 8;
        }
        if (matches(row.get("permissionKey"), request.permissionKey())) {
            score += 4;
        }
        if (matches(row.get("resourceCode"), request.resourceCode())) {
            score += 2;
        }
        if (matches(row.get("actionCode"), request.actionCode())) {
            score += 1;
        }
        return score;
    }

    private boolean matches(Object grantValue, String requestValue) {
        String value = trim(grantValue == null ? null : String.valueOf(grantValue));
        return StringUtils.hasText(value) && ("*".equals(value) || value.equals(trim(requestValue)));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? defaultValue : String.valueOf(value).trim();
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
