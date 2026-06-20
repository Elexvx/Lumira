package com.lumira.common.security.authorization;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAuthorizationServiceTest {

    private final DefaultAuthorizationService authorizationService = new DefaultAuthorizationService();

    @Test
    void allowsLegacyPermissionMatch() {
        AuthorizationDecision decision = authorizationService.evaluate(
                AuthorizationRequest.permission(currentUser(1001L, "system:user:view"), "system:user:view")
        );

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.ALLOW);
    }

    @Test
    void deniesBlankPermissionKey() {
        assertThatThrownBy(() -> authorizationService.require(AuthorizationRequest.permission(currentUser(1001L, "*"), "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Permission key is required");
    }

    @Test
    void deniesTenantMismatch() {
        AuthorizationRequest request = new AuthorizationRequest(
                2002L,
                SubjectRef.humanUser(2002L, 100L),
                null,
                100L,
                null,
                null,
                null,
                "system:user:view",
                null,
                "LOW",
                null,
                java.util.Map.of(),
                false,
                false,
                "WEB",
                null,
                null,
                currentUser(1001L, "system:user:view")
        );

        assertThatThrownBy(() -> authorizationService.require(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Tenant context does not match");
    }

    @Test
    void requireAllowsAuthorizedRequest() {
        assertThatCode(() -> authorizationService.require(
                AuthorizationRequest.permission(currentUser(1001L, "*"), "system:user:view")
        )).doesNotThrowAnyException();
    }

    @Test
    void requiresApprovalForHighRiskAgentTool() {
        AuthorizationDecision decision = authorizationService.evaluate(agentRequest(false, true, Map.of("agentGrant", "allow")));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.REQUIRE_APPROVAL);
        assertThat(decision.approvalRequired()).isTrue();
    }

    @Test
    void deniesAgentToolWithoutGrant() {
        AuthorizationDecision decision = authorizationService.evaluate(agentRequest(true, true, Map.of("agentGrant", "deny")));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("AGENT_GRANT_DENIED");
    }

    @Test
    void deniesCrossTenantDataScope() {
        AuthorizationDecision decision = authorizationService.evaluate(agentRequest(true, true, Map.of(
                "agentGrant", "allow",
                "resourceTenantId", 2002L
        )));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DATA_SCOPE_TENANT_MISMATCH");
    }

    private CurrentUser currentUser(Long tenantId, String permission) {
        return new CurrentUser(100L, "admin", tenantId, "session-1", 1, true, Set.of(permission));
    }

    private AuthorizationRequest agentRequest(boolean approvalGranted, boolean confirmed, Map<String, Object> arguments) {
        CurrentUser user = currentUser(1001L, "ai:tool:*");
        return new AuthorizationRequest(
                1001L,
                SubjectRef.humanUser(1001L, 100L),
                SubjectRef.digitalEmployee(1001L, 300L),
                100L,
                300L,
                "ai_tool",
                "execute",
                "ai:tool:file.delete",
                "file.delete",
                "HIGH",
                900L,
                arguments,
                confirmed,
                approvalGranted,
                "AI",
                "req-1",
                "trace-1",
                user
        );
    }
}
