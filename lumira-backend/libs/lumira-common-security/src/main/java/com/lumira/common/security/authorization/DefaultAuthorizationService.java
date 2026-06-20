package com.lumira.common.security.authorization;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultAuthorizationService implements AuthorizationService {

    @Override
    public AuthorizationDecision evaluate(AuthorizationRequest request) {
        if (request == null) {
            return AuthorizationDecision.deny("AUTHZ_REQUEST_MISSING", "Authorization request is missing");
        }
        if (request.tenantId() == null) {
            return AuthorizationDecision.deny("TENANT_MISSING", "Tenant context is required");
        }
        if (!StringUtils.hasText(request.permissionKey())) {
            return AuthorizationDecision.deny("PERMISSION_KEY_MISSING", "Permission key is required");
        }
        CurrentUser currentUser = request.currentUser();
        if (currentUser == null || currentUser.getPermissions() == null) {
            return AuthorizationDecision.deny("SUBJECT_PERMISSION_MISSING", "Subject permissions are missing");
        }
        if (currentUser.getCurrentTenantId() == null || !request.tenantId().equals(currentUser.getCurrentTenantId())) {
            return AuthorizationDecision.deny("TENANT_MISMATCH", "Tenant context does not match current user");
        }
        List<String> matched = new ArrayList<>();
        if (!hasRbacPermission(currentUser, request.permissionKey())) {
            return deny("RBAC_PERMISSION_MISSING", "Permission denied", matched);
        }
        matched.add("RBAC_PERMISSION_MATCH");

        AuthorizationDecision agentGrant = evaluateAgentGrant(request);
        if (!agentGrant.allowed()) {
            return agentGrant;
        }
        matched.addAll(agentGrant.matchedPolicies());

        AuthorizationDecision delegation = evaluateDelegation(request);
        if (!delegation.allowed()) {
            return delegation;
        }
        matched.addAll(delegation.matchedPolicies());

        AuthorizationDecision dataScope = evaluateDataScope(request, currentUser);
        if (!dataScope.allowed()) {
            return dataScope;
        }
        matched.addAll(dataScope.matchedPolicies());

        AuthorizationDecision risk = evaluateRisk(request);
        if (risk.verdict() == AuthorizationVerdict.DENY) {
            return risk;
        }
        matched.addAll(risk.matchedPolicies());
        if (risk.verdict() == AuthorizationVerdict.REQUIRE_APPROVAL || risk.verdict() == AuthorizationVerdict.REQUIRE_CONFIRM) {
            return new AuthorizationDecision(risk.verdict(), risk.reasonCode(), risk.message(), true,
                    risk.approvalRequired(), null, List.copyOf(matched));
        }

        return new AuthorizationDecision(AuthorizationVerdict.ALLOW, "AUTHZ_POLICY_ALLOW", "Permission granted",
                false, false, null, List.copyOf(matched));
    }

    @Override
    public void require(AuthorizationRequest request) {
        AuthorizationDecision decision = evaluate(request);
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.FORBIDDEN, decision.message());
        }
    }

    private boolean hasRbacPermission(CurrentUser currentUser, String permissionKey) {
        return currentUser.getPermissions().contains("*")
                || currentUser.getPermissions().contains(permissionKey)
                || currentUser.getPermissions().stream().anyMatch(permission -> wildcardMatches(permission, permissionKey));
    }

    private boolean wildcardMatches(String granted, String required) {
        if (!StringUtils.hasText(granted) || !StringUtils.hasText(required) || !granted.endsWith("*")) {
            return false;
        }
        String prefix = granted.substring(0, granted.length() - 1);
        return required.startsWith(prefix);
    }

    private AuthorizationDecision evaluateAgentGrant(AuthorizationRequest request) {
        if (request.agentSubject() == null && request.employeeId() == null && !StringUtils.hasText(request.toolCode())) {
            return AuthorizationDecision.allow("AGENT_NOT_IN_SCOPE", "No agent grant required");
        }
        if (request.employeeId() == null || request.employeeId() <= 0) {
            return deny("AGENT_SUBJECT_MISSING", "Agent subject is required", List.of());
        }
        if (!StringUtils.hasText(request.toolCode())) {
            return deny("AGENT_TOOL_MISSING", "Agent tool grant is required", List.of());
        }
        String mode = normalizeArgument(request.arguments(), "agentGrant", "permissionMode");
        if (List.of("deny", "blocked", "none").contains(mode)) {
            return deny("AGENT_GRANT_DENIED", "Agent grant denied", List.of());
        }
        if (StringUtils.hasText(mode) && !List.of("view", "visit", "invoke", "execute", "allow").contains(mode)) {
            return deny("AGENT_GRANT_INVALID", "Agent grant is invalid", List.of());
        }
        return AuthorizationDecision.allow("AGENT_GRANT_ALLOW", "Agent grant matched");
    }

    private AuthorizationDecision evaluateDelegation(AuthorizationRequest request) {
        if (request.agentSubject() == null && request.employeeId() == null) {
            return AuthorizationDecision.allow("DELEGATION_NOT_IN_SCOPE", "No delegation required");
        }
        Long humanUserId = request.humanUserId() == null && request.currentUser() != null
                ? request.currentUser().getUserId()
                : request.humanUserId();
        if (humanUserId == null || humanUserId <= 0) {
            return deny("DELEGATION_HUMAN_MISSING", "Delegating human subject is required", List.of());
        }
        if (Boolean.FALSE.equals(argumentBoolean(request.arguments(), "delegationAllowed"))) {
            return deny("DELEGATION_DENIED", "Delegation is not allowed", List.of());
        }
        return AuthorizationDecision.allow("DELEGATION_ALLOW", "Delegation allowed");
    }

    private AuthorizationDecision evaluateDataScope(AuthorizationRequest request, CurrentUser currentUser) {
        Long resourceTenantId = argumentLong(request.arguments(), "resourceTenantId", "tenantId");
        if (resourceTenantId != null && !request.tenantId().equals(resourceTenantId)) {
            return deny("DATA_SCOPE_TENANT_MISMATCH", "Resource tenant is outside current scope", List.of());
        }
        String dataScope = normalizeArgument(request.arguments(), "dataScope");
        Long ownerUserId = argumentLong(request.arguments(), "ownerUserId", "createdBy");
        if ("self".equals(dataScope) && ownerUserId != null && !ownerUserId.equals(currentUser.getUserId())) {
            return deny("DATA_SCOPE_SELF_DENIED", "Resource is outside self data scope", List.of());
        }
        if ("none".equals(dataScope) || "deny".equals(dataScope)) {
            return deny("DATA_SCOPE_DENIED", "Data scope denies this resource", List.of());
        }
        return AuthorizationDecision.allow("DATA_SCOPE_ALLOW", "Data scope matched");
    }

    private AuthorizationDecision evaluateRisk(AuthorizationRequest request) {
        String risk = StringUtils.hasText(request.riskLevel())
                ? request.riskLevel().trim().toUpperCase(Locale.ROOT)
                : "LOW";
        if (Boolean.TRUE.equals(argumentBoolean(request.arguments(), "readOnly")) && isWriteAction(request)) {
            return new AuthorizationDecision(AuthorizationVerdict.READ_ONLY, "READ_ONLY_SCOPE", "Read-only grant cannot perform write action",
                    true, false, null, List.of("READ_ONLY_SCOPE"));
        }
        if (List.of("CRITICAL", "HIGH").contains(risk) && !request.approvalGranted()) {
            return new AuthorizationDecision(AuthorizationVerdict.REQUIRE_APPROVAL, "RISK_APPROVAL_REQUIRED", "Approval is required",
                    true, true, null, List.of("RISK_APPROVAL_REQUIRED"));
        }
        if ("MEDIUM".equals(risk) && !request.confirmed()) {
            return new AuthorizationDecision(AuthorizationVerdict.REQUIRE_CONFIRM, "RISK_CONFIRM_REQUIRED", "Confirmation is required",
                    true, false, null, List.of("RISK_CONFIRM_REQUIRED"));
        }
        return AuthorizationDecision.allow("RISK_ACCEPTED", "Risk accepted");
    }

    private boolean isWriteAction(AuthorizationRequest request) {
        String action = StringUtils.hasText(request.actionCode()) ? request.actionCode().toLowerCase(Locale.ROOT) : "";
        return !(action.startsWith("read") || action.startsWith("view") || action.startsWith("list") || action.startsWith("search"));
    }

    private AuthorizationDecision deny(String reasonCode, String message, List<String> matched) {
        List<String> policies = new ArrayList<>(matched);
        policies.add(reasonCode);
        return new AuthorizationDecision(AuthorizationVerdict.DENY, reasonCode, message, true, false, null, List.copyOf(policies));
    }

    private String normalizeArgument(Map<String, Object> arguments, String... keys) {
        if (arguments == null) {
            return "";
        }
        for (String key : keys) {
            Object value = arguments.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim().toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    private Boolean argumentBoolean(Map<String, Object> arguments, String key) {
        if (arguments == null || !arguments.containsKey(key)) {
            return null;
        }
        Object value = arguments.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Long argumentLong(Map<String, Object> arguments, String... keys) {
        if (arguments == null) {
            return null;
        }
        for (String key : keys) {
            Object value = arguments.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null && StringUtils.hasText(value.toString())) {
                try {
                    return Long.parseLong(value.toString().trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
