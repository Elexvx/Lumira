package com.lumira.common.security.authorization;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAuthorizationServiceTest {

    @Test
    void deniesEmptyRequestAndIncompleteTargets() {
        DefaultAuthorizationService service = service(allowGrant("ai:tool:file.delete", "HIGH", false, false));

        assertThat(service.evaluate(null).reasonCode()).isEqualTo("AUTHZ_REQUEST_MISSING");
        assertThat(service.evaluate(request(null, "WEB", user(1001L, "x"), "x", null, null, null)).reasonCode()).isEqualTo("TENANT_MISSING");
        assertThat(service.evaluate(request(1001L, "WEB", user(1001L, "*"), null, null, null, null)).reasonCode()).isEqualTo("AUTHZ_TARGET_MISSING");
    }

    @Test
    void allowsAndDeniesHumanPermission() {
        DefaultAuthorizationService service = service(allowGrant("unused", "LOW", false, false));

        assertThat(service.evaluate(AuthorizationRequest.permission(user(1001L, "system:user:view"), "system:user:view")).verdict())
                .isEqualTo(AuthorizationVerdict.ALLOW);
        assertThat(service.evaluate(AuthorizationRequest.permission(user(1001L, "system:user:view"), "system:user:delete")).reasonCode())
                .isEqualTo("RBAC_PERMISSION_MISSING");
    }

    @Test
    void deniesTenantMismatchAndCrossTenantScope() {
        DefaultAuthorizationService service = service(allowGrant("ai:tool:file.delete", "HIGH", false, false));

        assertThat(service.evaluate(request(2002L, "WEB", user(1001L, "system:user:view"), "system:user:view", null, null, null)).reasonCode())
                .isEqualTo("TENANT_MISMATCH");
        assertThat(service.evaluate(aiRequest(user(1001L, "ai:tool:*"), 300L, "file.delete", "ai:tool:file.delete", "LOW", true, true,
                Map.of("resourceTenantId", 2002L))).reasonCode()).isEqualTo("DATA_SCOPE_TENANT_MISMATCH");
    }

    @Test
    void aiAgentRequiresUserAndAgentIntersection() {
        DefaultAuthorizationService allowed = service(allowGrant("ai:tool:file.delete", "HIGH", false, false));
        assertThat(allowed.evaluate(aiRequest(user(1001L, "ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "LOW", true, true, Map.of())).verdict()).isEqualTo(AuthorizationVerdict.ALLOW);

        DefaultAuthorizationService noGrant = service(AgentToolGrantDecision.deny("AGENT_GRANT_DENIED"));
        assertThat(noGrant.evaluate(aiRequest(user(1001L, "ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "LOW", true, true, Map.of())).reasonCode()).isEqualTo("AGENT_GRANT_DENIED");

        assertThat(allowed.evaluate(aiRequest(user(1001L, "system:user:view"), 300L, "file.delete",
                "ai:tool:file.delete", "LOW", true, true, Map.of())).reasonCode()).isEqualTo("RBAC_PERMISSION_MISSING");
    }

    @Test
    void aiAgentRequiresDelegationGrant() {
        DefaultAuthorizationService noDelegation = new DefaultAuthorizationService(
                request -> allowGrant("ai:tool:file.delete", "HIGH", false, false),
                request -> DelegationGrantDecision.deny("DELEGATION_GRANT_NOT_FOUND", "Delegation grant was not found")
        );

        assertThat(noDelegation.evaluate(aiRequest(user(1001L, "ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "LOW", true, true, Map.of())).reasonCode()).isEqualTo("DELEGATION_GRANT_NOT_FOUND");
    }

    @Test
    void aiAgentEnforcesRiskConfirmAndApproval() {
        DefaultAuthorizationService service = service(allowGrant("ai:tool:file.delete", "HIGH", false, false));

        assertThat(service.evaluate(aiRequest(user(1001L, "ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "CRITICAL", true, true, Map.of())).reasonCode()).isEqualTo("AGENT_RISK_EXCEEDS_GRANT");
        assertThat(service.evaluate(aiRequest(user(1001L, "ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "HIGH", false, true, Map.of())).verdict()).isEqualTo(AuthorizationVerdict.REQUIRE_CONFIRM);

        DefaultAuthorizationService approval = service(allowGrant("ai:tool:file.delete", "CRITICAL", false, true));
        assertThat(approval.evaluate(aiRequest(user(1001L, "ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "CRITICAL", true, false, Map.of())).verdict()).isEqualTo(AuthorizationVerdict.REQUIRE_APPROVAL);
    }

    @Test
    void pluginAndSystemJobDefaultDeny() {
        DefaultAuthorizationService service = service(allowGrant("unused", "LOW", false, false));

        assertThat(service.evaluate(request(1001L, "PLUGIN", user(1001L, "*"), null, null, "plugin-x", null)).reasonCode())
                .isEqualTo("PLUGIN_PERMISSION_MISSING");
        assertThat(service.evaluate(request(1001L, "SYSTEM_JOB", null, null, "file", null, "test")).reasonCode())
                .isEqualTo("SYSTEM_PRINCIPAL_MISSING");
        assertThat(service.evaluate(AuthorizationRequest.systemJob(1001L, "file", "test", "req-1")).verdict())
                .isEqualTo(AuthorizationVerdict.ALLOW);
    }

    @Test
    void requireAllowsAuthorizedRequest() {
        assertThatCode(() -> service(allowGrant("unused", "LOW", false, false))
                .require(AuthorizationRequest.permission(user(1001L, "*"), "system:user:view"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> service(allowGrant("unused", "LOW", false, false))
                .require(AuthorizationRequest.permission(user(1001L, "system:user:view"), "system:user:delete")))
                .isInstanceOf(BizException.class);
    }

    private DefaultAuthorizationService service(AgentToolGrantDecision decision) {
        return new DefaultAuthorizationService(request -> decision,
                request -> DelegationGrantDecision.allow("TENANT", "CRITICAL", false, false, List.of("TEST_DELEGATION")));
    }

    private AgentToolGrantDecision allowGrant(String permissionKey, String maxRisk, boolean confirm, boolean approval) {
        return AgentToolGrantDecision.allow("EXECUTE", permissionKey, maxRisk, confirm, approval, List.of("TEST_GRANT"));
    }

    private CurrentUser user(Long tenantId, String permission) {
        return new CurrentUser(100L, "admin", tenantId, "session-1", 1, true, Set.of(permission));
    }

    private AuthorizationRequest request(Long tenantId, String channel, CurrentUser user, String permissionKey,
                                         String resource, String tool, String action) {
        return new AuthorizationRequest(tenantId, null, null, user == null ? null : user.getUserId(), null,
                resource, action, permissionKey, tool, "LOW", null, Map.of(), false, false, channel,
                "req-1", "trace-1", user);
    }

    private AuthorizationRequest aiRequest(CurrentUser user, Long employeeId, String toolCode, String permissionKey,
                                           String riskLevel, boolean confirmed, boolean approvalGranted,
                                           Map<String, Object> arguments) {
        return AuthorizationRequest.aiTool(user, employeeId, toolCode, permissionKey, riskLevel, confirmed, approvalGranted, arguments);
    }
}
