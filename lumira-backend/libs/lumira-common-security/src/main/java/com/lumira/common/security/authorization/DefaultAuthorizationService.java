package com.lumira.common.security.authorization;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultAuthorizationService implements AuthorizationService {

    private final AgentToolGrantEvaluator agentToolGrantEvaluator;
    private final DelegationGrantEvaluator delegationGrantEvaluator;

    public DefaultAuthorizationService() {
        this(request -> AgentToolGrantDecision.deny("AGENT_GRANT_EVALUATOR_MISSING"),
                request -> DelegationGrantDecision.deny("DELEGATION_GRANT_EVALUATOR_MISSING", "Delegation grant evaluator is missing"));
    }

    public DefaultAuthorizationService(AgentToolGrantEvaluator agentToolGrantEvaluator) {
        this(agentToolGrantEvaluator, null);
    }

    @Autowired(required = false)
    public DefaultAuthorizationService(AgentToolGrantEvaluator agentToolGrantEvaluator,
                                       DelegationGrantEvaluator delegationGrantEvaluator) {
        this.agentToolGrantEvaluator = agentToolGrantEvaluator == null
                ? request -> AgentToolGrantDecision.deny("AGENT_GRANT_EVALUATOR_MISSING")
                : agentToolGrantEvaluator;
        this.delegationGrantEvaluator = delegationGrantEvaluator == null
                ? request -> DelegationGrantDecision.deny("DELEGATION_GRANT_EVALUATOR_MISSING", "Delegation grant evaluator is missing")
                : delegationGrantEvaluator;
    }

    @Override
    public AuthorizationDecision evaluate(AuthorizationRequest request) {
        if (request == null) {
            return AuthorizationDecision.deny("AUTHZ_REQUEST_MISSING", "Authorization request is missing");
        }
        if (request.tenantId() == null) {
            return AuthorizationDecision.deny("TENANT_MISSING", "Tenant context is required");
        }
        String channel = normalizeChannel(request.channel());
        if (!StringUtils.hasText(channel)) {
            return AuthorizationDecision.deny("CHANNEL_MISSING", "Authorization channel is required");
        }
        String requiredPermission = resolvePermissionKey(request);
        if (!StringUtils.hasText(requiredPermission) && !StringUtils.hasText(request.toolCode())) {
            return AuthorizationDecision.deny("AUTHZ_TARGET_MISSING", "Authorization target is required");
        }
        if ("PLUGIN".equals(channel) && !StringUtils.hasText(requiredPermission)) {
            return AuthorizationDecision.deny("PLUGIN_PERMISSION_MISSING", "Plugin permission is required");
        }
        if ("SYSTEM_JOB".equals(channel)) {
            return evaluateSystemJob(request);
        }
        List<String> matched = new ArrayList<>();
        CurrentUser currentUser = request.currentUser();
        if (currentUser == null) {
            return AuthorizationDecision.deny("CURRENT_USER_MISSING", "Current user is required");
        }
        if (currentUser.getCurrentTenantId() == null || !request.tenantId().equals(currentUser.getCurrentTenantId())) {
            return AuthorizationDecision.deny("TENANT_MISMATCH", "Tenant context does not match current user");
        }
        if (currentUser.getPermissions() == null || currentUser.getPermissions().isEmpty()) {
            return AuthorizationDecision.deny("SUBJECT_PERMISSION_MISSING", "Subject permissions are missing");
        }
        if (StringUtils.hasText(requiredPermission) && !hasRbacPermission(currentUser, requiredPermission)) {
            return deny("RBAC_PERMISSION_MISSING", "Permission denied", matched);
        }
        matched.add("RBAC_PERMISSION_MATCH");

        AuthorizationDecision agentGrant = evaluateAgentGrant(request, requiredPermission, channel);
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
        if (risk.verdict() == AuthorizationVerdict.REQUIRE_APPROVAL) {
            return AuthorizationDecision.requireApproval(risk.reasonCode(), risk.message(), List.copyOf(matched));
        }
        if (risk.verdict() == AuthorizationVerdict.REQUIRE_CONFIRM) {
            return AuthorizationDecision.requireConfirm(risk.reasonCode(), risk.message(), List.copyOf(matched));
        }

        return new AuthorizationDecision(AuthorizationVerdict.ALLOW, "AUTHZ_POLICY_ALLOW", "Permission granted",
                isHighRisk(request), false, false, "default-enterprise-pdp", List.copyOf(matched), "tenant");
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

    private AuthorizationDecision evaluateAgentGrant(AuthorizationRequest request, String requiredPermission, String channel) {
        if (!"AI_AGENT".equals(channel) && request.employeeId() == null) {
            return AuthorizationDecision.allow("AGENT_NOT_IN_SCOPE", "No agent grant required");
        }
        if (request.employeeId() == null || request.employeeId() <= 0) {
            return deny("AGENT_SUBJECT_MISSING", "Agent subject is required", List.of());
        }
        if (!StringUtils.hasText(request.toolCode())) {
            return deny("AGENT_TOOL_MISSING", "Agent tool grant is required", List.of());
        }
        AgentToolGrantDecision grant = agentToolGrantEvaluator.evaluate(request);
        if (grant == null || !grant.allowed()) {
            return deny(grant == null ? "AGENT_GRANT_DENIED" : grant.reasonCode(), "Agent grant denied", List.of());
        }
        String mode = normalizeText(grant.permissionMode());
        boolean readOnlyCheck = !isWriteAction(request);
        if (readOnlyCheck) {
            if (!List.of("view", "visit", "execute", "invoke").contains(mode)) {
                return deny("AGENT_GRANT_VIEW_DENIED", "Agent grant does not allow viewing this tool", grant.matchedPolicies());
            }
        } else if (!List.of("execute", "invoke").contains(mode)) {
            return deny("AGENT_GRANT_EXECUTE_DENIED", "Agent grant does not allow execution", grant.matchedPolicies());
        }
        if (StringUtils.hasText(grant.permissionKey()) && StringUtils.hasText(requiredPermission)
                && !permissionCompatible(grant.permissionKey(), requiredPermission)) {
            return deny("AGENT_USER_PERMISSION_INTERSECTION_EMPTY", "Agent grant and user permission do not intersect", grant.matchedPolicies());
        }
        if (riskExceeds(request.riskLevel(), grant.maxRiskLevel())) {
            return deny("AGENT_RISK_EXCEEDS_GRANT", "Agent tool risk exceeds grant", grant.matchedPolicies());
        }
        if ((grant.requireApproval() || "CRITICAL".equals(normalizeRisk(request.riskLevel()))) && !request.approvalGranted()) {
            return AuthorizationDecision.requireApproval("AGENT_APPROVAL_REQUIRED", "Approval is required", grant.matchedPolicies());
        }
        if ((grant.requireConfirm() || "HIGH".equals(normalizeRisk(request.riskLevel()))) && !request.confirmed()) {
            return AuthorizationDecision.requireConfirm("AGENT_CONFIRM_REQUIRED", "Confirmation is required", grant.matchedPolicies());
        }
        return AuthorizationDecision.allow("AGENT_GRANT_ALLOW", "Agent grant matched");
    }

    private AuthorizationDecision evaluateDelegation(AuthorizationRequest request) {
        if (!"AI_AGENT".equals(normalizeChannel(request.channel())) && request.employeeId() == null) {
            return AuthorizationDecision.allow("DELEGATION_NOT_IN_SCOPE", "No delegation required");
        }
        DelegationGrantDecision grant = delegationGrantEvaluator.evaluate(request);
        if (grant == null) {
            return deny("DELEGATION_GRANT_DENIED", "Delegation grant denied", List.of());
        }
        if (grant.verdict() == AuthorizationVerdict.REQUIRE_APPROVAL) {
            return AuthorizationDecision.requireApproval(grant.reasonCode(), grant.message(), grant.matchedPolicies());
        }
        if (grant.verdict() == AuthorizationVerdict.REQUIRE_CONFIRM) {
            return AuthorizationDecision.requireConfirm(grant.reasonCode(), grant.message(), grant.matchedPolicies());
        }
        if (!grant.allowed()) {
            return deny(grant.reasonCode(), grant.message(), grant.matchedPolicies());
        }
        if (riskExceeds(request.riskLevel(), grant.maxRiskLevel())) {
            return deny("DELEGATION_RISK_EXCEEDS_GRANT", "Delegation grant risk limit exceeded", grant.matchedPolicies());
        }
        if (grant.requireApproval() && !request.approvalGranted()) {
            return AuthorizationDecision.requireApproval("DELEGATION_APPROVAL_REQUIRED", "Delegation approval is required", grant.matchedPolicies());
        }
        if (grant.requireConfirm() && !request.confirmed()) {
            return AuthorizationDecision.requireConfirm("DELEGATION_CONFIRM_REQUIRED", "Delegation confirmation is required", grant.matchedPolicies());
        }
        String scope = normalizeText(grant.scopeType());
        if ("deny".equals(scope) || "none".equals(scope)) {
            return deny("DELEGATION_SCOPE_DENIED", "Delegation scope denies this request", grant.matchedPolicies());
        }
        if ("custom".equals(scope)) {
            return deny("DELEGATION_CUSTOM_SCOPE_UNRESOLVED", "Delegation custom scope is not resolvable", grant.matchedPolicies());
        }
        if ("self".equals(scope)) {
            Long ownerUserId = argumentLong(request.arguments(), "ownerUserId", "createdBy", "userId");
            Long humanUserId = request.humanUserId() == null && request.currentUser() != null
                    ? request.currentUser().getUserId()
                    : request.humanUserId();
            if (ownerUserId != null && humanUserId != null && !ownerUserId.equals(humanUserId)) {
                return deny("DELEGATION_SCOPE_SELF_DENIED", "Delegation scope is limited to self data", grant.matchedPolicies());
            }
        }
        return new AuthorizationDecision(AuthorizationVerdict.ALLOW, grant.reasonCode(), grant.message(),
                false, false, false, "default-enterprise-pdp", grant.matchedPolicies(), scope);
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
                    true, false, false, null, List.of("READ_ONLY_SCOPE"), "read-only");
        }
        if ("CRITICAL".equals(risk) && !request.approvalGranted()) {
            return AuthorizationDecision.requireApproval("RISK_APPROVAL_REQUIRED", "Approval is required", List.of("RISK_APPROVAL_REQUIRED"));
        }
        if ("HIGH".equals(risk) && !request.confirmed()) {
            return AuthorizationDecision.requireConfirm("RISK_CONFIRM_REQUIRED", "Confirmation is required", List.of("RISK_CONFIRM_REQUIRED"));
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
        return new AuthorizationDecision(AuthorizationVerdict.DENY, reasonCode, message, true, false, false, "default-enterprise-pdp", List.copyOf(policies), "none");
    }

    private AuthorizationDecision evaluateSystemJob(AuthorizationRequest request) {
        if (!Boolean.TRUE.equals(argumentBoolean(request.arguments(), "systemPrincipal"))
                && !Boolean.TRUE.equals(argumentBoolean(request.arguments(), "internalToken"))) {
            return AuthorizationDecision.deny("SYSTEM_PRINCIPAL_MISSING", "System principal is required");
        }
        return new AuthorizationDecision(AuthorizationVerdict.ALLOW, "SYSTEM_JOB_ALLOW", "Permission granted",
                true, false, false, "default-enterprise-pdp", List.of("SYSTEM_JOB_ALLOW"), "system");
    }

    private String resolvePermissionKey(AuthorizationRequest request) {
        if (StringUtils.hasText(request.permissionKey())) {
            return request.permissionKey().trim();
        }
        if (StringUtils.hasText(request.resourceCode()) && StringUtils.hasText(request.actionCode())) {
            return request.resourceCode().trim() + ":" + request.actionCode().trim();
        }
        return "";
    }

    private String normalizeChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            return "";
        }
        String normalized = channel.trim().toUpperCase(Locale.ROOT);
        return "AI".equals(normalized) ? "AI_AGENT" : normalized;
    }

    private boolean permissionCompatible(String grantPermission, String requiredPermission) {
        return hasPermissionString(grantPermission, requiredPermission) || hasPermissionString(requiredPermission, grantPermission);
    }

    private boolean hasPermissionString(String granted, String required) {
        return "*".equals(granted) || granted.equals(required) || wildcardMatches(granted, required);
    }

    private boolean riskExceeds(String actualRisk, String maxRisk) {
        return riskRank(actualRisk) > riskRank(maxRisk);
    }

    private int riskRank(String risk) {
        return switch (normalizeRisk(risk)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private String normalizeRisk(String risk) {
        return StringUtils.hasText(risk) ? risk.trim().toUpperCase(Locale.ROOT) : "LOW";
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private boolean isHighRisk(AuthorizationRequest request) {
        return List.of("HIGH", "CRITICAL").contains(normalizeRisk(request.riskLevel()));
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
