package com.lumira.saas.modules.iam.app;

import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationVerdict;
import com.lumira.common.security.authorization.DelegationGrantDecision;
import com.lumira.common.security.authorization.DelegationGrantEvaluator;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultDelegationGrantEvaluator implements DelegationGrantEvaluator {

    private final MyBatisQueryOperations jdbcTemplate;

    public DefaultDelegationGrantEvaluator(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DelegationGrantDecision evaluate(AuthorizationRequest request) {
        if (request == null || (!"AI_AGENT".equals(normalize(request.channel())) && request.employeeId() == null)) {
            return DelegationGrantDecision.notInScope();
        }
        Long tenantId = request.tenantId();
        Long humanUserId = request.humanUserId() == null && request.currentUser() != null
                ? request.currentUser().getUserId()
                : request.humanUserId();
        Long employeeId = request.employeeId();
        if (tenantId == null || humanUserId == null || humanUserId <= 0 || employeeId == null || employeeId <= 0) {
            return DelegationGrantDecision.deny("DELEGATION_CONTEXT_INCOMPLETE", "Delegation subject context is incomplete");
        }
        Long humanSubjectId = subjectId(tenantId, "HUMAN_USER", humanUserId);
        if (humanSubjectId == null) {
            return DelegationGrantDecision.deny("DELEGATION_HUMAN_SUBJECT_NOT_FOUND", "Delegating human subject was not found");
        }
        Long employeeSubjectId = subjectId(tenantId, "DIGITAL_EMPLOYEE", employeeId);
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
                        where tenant_id = ?
                          and delegator_subject_id = ?
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
                tenantId,
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

    private Long subjectId(Long tenantId, String subjectType, Long refId) {
        return jdbcTemplate.queryForObject(
                """
                        select id
                        from iam_subject
                        where tenant_id = ?
                          and subject_type = ?
                          and ref_id = ?
                          and status = 'ENABLED'
                          and deleted = 0
                        limit 1
                        """,
                Long.class,
                tenantId,
                subjectType,
                refId
        );
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
