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
        assertThat(service.evaluate(request("WEB", user("*"), null, null, null, null)).reasonCode()).isEqualTo("AUTHZ_TARGET_MISSING");
    }

    @Test
    void allowsAndDeniesHumanPermission() {
        DefaultAuthorizationService service = service(allowGrant("unused", "LOW", false, false));

        assertThat(service.evaluate(AuthorizationRequest.permission(user("system:user:view"), "system:user:view")).verdict())
                .isEqualTo(AuthorizationVerdict.ALLOW);
        assertThat(service.evaluate(AuthorizationRequest.permission(user("system:user:view"), "system:user:delete")).reasonCode())
                .isEqualTo("RBAC_PERMISSION_MISSING");
    }

    @Test
    void deniesUnauthenticatedSubjectEvenWhenPermissionIsPresent() {
        DefaultAuthorizationService service = service(allowGrant("unused", "LOW", false, false));
        CurrentUser unauthenticated = user("system:user:view");
        unauthenticated.setAuthenticated(false);
        CurrentUser invalidUserId = user("system:user:view");
        invalidUserId.setUserId(0L);
        CurrentUser missingUserId = user("system:user:view");
        missingUserId.setUserId(null);
        CurrentUser blankUsername = user("system:user:view");
        blankUsername.setUsername(" ");
        CurrentUser missingSessionId = user("system:user:view");
        missingSessionId.setSessionId(null);
        CurrentUser missingSessionVersion = user("system:user:view");
        missingSessionVersion.setSessionVersion(null);
        CurrentUser missingUserUuid = user("system:user:view");
        missingUserUuid.setUserUuid(null);
        CurrentUser missingPermissionsVersion = user("system:user:view");
        missingPermissionsVersion.setPermissionsVersion(null);

        assertThat(service.evaluate(AuthorizationRequest.permission(unauthenticated, "system:user:view")).reasonCode())
                .isEqualTo("SUBJECT_UNAUTHENTICATED");
        assertThat(service.evaluate(AuthorizationRequest.permission(invalidUserId, "system:user:view")).reasonCode())
                .isEqualTo("SUBJECT_UNAUTHENTICATED");
        assertThat(service.evaluate(AuthorizationRequest.permission(missingUserId, "system:user:view")).reasonCode())
                .isEqualTo("SUBJECT_UNAUTHENTICATED");
        assertThat(service.evaluate(AuthorizationRequest.permission(blankUsername, "system:user:view")).reasonCode())
                .isEqualTo("SUBJECT_UNAUTHENTICATED");
        assertThat(service.evaluate(AuthorizationRequest.permission(missingSessionId, "system:user:view")).reasonCode())
                .isEqualTo("SUBJECT_UNAUTHENTICATED");
        assertThat(service.evaluate(AuthorizationRequest.permission(missingSessionVersion, "system:user:view")).reasonCode())
                .isEqualTo("SUBJECT_UNAUTHENTICATED");
        assertThat(service.evaluate(AuthorizationRequest.permission(missingUserUuid, "system:user:view")).reasonCode())
                .isEqualTo("SUBJECT_UNAUTHENTICATED");
        assertThat(service.evaluate(AuthorizationRequest.permission(missingPermissionsVersion, "system:user:view")).reasonCode())
                .isEqualTo("SUBJECT_UNAUTHENTICATED");
    }

    @Test
    void authorizationRequestFactoriesDoNotInferSubjectFromUntrustedCurrentUser() {
        CurrentUser missingSessionVersion = user("*");
        missingSessionVersion.setSessionVersion(null);
        CurrentUser missingUserUuid = user("*");
        missingUserUuid.setUserUuid(null);
        CurrentUser missingPermissionsVersion = user("*");
        missingPermissionsVersion.setPermissionsVersion(null);

        assertThat(AuthorizationRequest.permission(missingSessionVersion, "system:user:view").humanUserId()).isNull();
        assertThat(AuthorizationRequest.permission(missingSessionVersion, "system:user:view").humanSubject().refId()).isNull();
        assertThat(AuthorizationRequest.aiTool(missingSessionVersion, 300L, "file.object.search", "system:file:view", "LOW", true, true, Map.of()).humanUserId()).isNull();
        assertThat(AuthorizationRequest.aiToolView(missingSessionVersion, 300L, "file.object.search", "system:file:view", "LOW", Map.of()).humanUserId()).isNull();
        assertThat(AuthorizationRequest.plugin(missingSessionVersion, "plugin:view", "demo").humanUserId()).isNull();
        assertThat(AuthorizationRequest.permission(missingUserUuid, "system:user:view").humanUserId()).isNull();
        assertThat(AuthorizationRequest.permission(missingPermissionsVersion, "system:user:view").humanUserId()).isNull();
    }

    @Test
    void allowsRbacWithoutResourceScope() {
        DefaultAuthorizationService service = service(allowGrant("ai:tool:file.delete", "HIGH", false, false));

        assertThat(service.evaluate(request("WEB", user("system:user:view"), "system:user:view", null, null, null)).verdict())
                .isEqualTo(AuthorizationVerdict.ALLOW);
        assertThat(service.evaluate(aiRequest(user("ai:tool:*"), 300L, "file.delete", "ai:tool:file.delete", "LOW", true, true,
                Map.of())).verdict()).isEqualTo(AuthorizationVerdict.ALLOW);
    }

    @Test
    void dataScopeSelfRequiresMatchingUserUuid() {
        DefaultAuthorizationService service = service(allowGrant("unused", "LOW", false, false));
        CurrentUser currentUser = user("system:user:view");

        assertThat(service.evaluate(request("WEB", currentUser, "system:user:view", null, null, null,
                Map.of("dataScope", "self", "ownerUserId", 100L, "ownerUserUuid", "user-uuid-100"))).verdict())
                .isEqualTo(AuthorizationVerdict.ALLOW);
        assertThat(service.evaluate(request("WEB", currentUser, "system:user:view", null, null, null,
                Map.of("dataScope", "self", "ownerUserUuid", "user-uuid-100"))).reasonCode())
                .isEqualTo("DATA_SCOPE_SELF_DENIED");
        assertThat(service.evaluate(request("WEB", currentUser, "system:user:view", null, null, null,
                Map.of("dataScope", "self", "ownerUserId", 100L))).reasonCode())
                .isEqualTo("DATA_SCOPE_SELF_DENIED");
        assertThat(service.evaluate(request("WEB", currentUser, "system:user:view", null, null, null,
                Map.of("dataScope", "self", "ownerUserId", 100L, "ownerUserUuid", "other-uuid"))).reasonCode())
                .isEqualTo("DATA_SCOPE_SELF_DENIED");
    }

    @Test
    void aiAgentRequiresUserAndAgentIntersection() {
        DefaultAuthorizationService allowed = service(allowGrant("ai:tool:file.delete", "HIGH", false, false));
        assertThat(allowed.evaluate(aiRequest(user("ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "LOW", true, true, Map.of())).verdict()).isEqualTo(AuthorizationVerdict.ALLOW);

        DefaultAuthorizationService noGrant = service(AgentToolGrantDecision.deny("AGENT_GRANT_DENIED"));
        assertThat(noGrant.evaluate(aiRequest(user("ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "LOW", true, true, Map.of())).reasonCode()).isEqualTo("AGENT_GRANT_DENIED");

        assertThat(allowed.evaluate(aiRequest(user("system:user:view"), 300L, "file.delete",
                "ai:tool:file.delete", "LOW", true, true, Map.of())).reasonCode()).isEqualTo("RBAC_PERMISSION_MISSING");
    }

    @Test
    void aiAgentRequiresDelegationGrant() {
        DefaultAuthorizationService noDelegation = new DefaultAuthorizationService(
                request -> allowGrant("ai:tool:file.delete", "HIGH", false, false),
                request -> DelegationGrantDecision.deny("DELEGATION_GRANT_NOT_FOUND", "Delegation grant was not found")
        );

        assertThat(noDelegation.evaluate(aiRequest(user("ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "LOW", true, true, Map.of())).reasonCode()).isEqualTo("DELEGATION_GRANT_NOT_FOUND");
    }

    @Test
    void aiAgentEnforcesRiskConfirmAndApproval() {
        DefaultAuthorizationService service = service(allowGrant("ai:tool:file.delete", "HIGH", false, false));

        assertThat(service.evaluate(aiRequest(user("ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "CRITICAL", true, true, Map.of())).reasonCode()).isEqualTo("AGENT_RISK_EXCEEDS_GRANT");
        assertThat(service.evaluate(aiRequest(user("ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "HIGH", false, true, Map.of())).verdict()).isEqualTo(AuthorizationVerdict.REQUIRE_CONFIRM);

        DefaultAuthorizationService approval = service(allowGrant("ai:tool:file.delete", "CRITICAL", false, true));
        assertThat(approval.evaluate(aiRequest(user("ai:tool:file.delete"), 300L, "file.delete",
                "ai:tool:file.delete", "CRITICAL", true, false, Map.of())).verdict()).isEqualTo(AuthorizationVerdict.REQUIRE_APPROVAL);
    }

    @Test
    void pluginAndSystemJobDefaultDeny() {
        DefaultAuthorizationService service = service(allowGrant("unused", "LOW", false, false));

        assertThat(service.evaluate(request("PLUGIN", user("*"), null, null, "plugin-x", null)).reasonCode())
                .isEqualTo("PLUGIN_PERMISSION_MISSING");
        assertThat(service.evaluate(request("SYSTEM_JOB", null, null, "file", null, "test")).reasonCode())
                .isEqualTo("SYSTEM_PRINCIPAL_MISSING");
        assertThat(service.evaluate(AuthorizationRequest.systemJob("file", "test", "req-1")).verdict())
                .isEqualTo(AuthorizationVerdict.ALLOW);
    }

    @Test
    void systemJobRejectsLegacyInternalTokenFlagAndMixedSubjects() {
        DefaultAuthorizationService service = service(allowGrant("unused", "LOW", false, false));

        AuthorizationRequest legacyInternalTokenRequest = request("SYSTEM_JOB", null, null, "file", null, "test",
                Map.of("internalToken", Boolean.TRUE));
        assertThat(service.evaluate(legacyInternalTokenRequest).reasonCode())
                .isEqualTo("SYSTEM_PRINCIPAL_MISSING");

        AuthorizationRequest mixedSubjectRequest = new AuthorizationRequest(
                SubjectRef.humanUser(100L),
                null,
                100L,
                "user-uuid-100",
                null,
                "file",
                "test",
                null,
                null,
                "LOW",
                null,
                Map.of("systemPrincipal", Boolean.TRUE),
                false,
                true,
                "SYSTEM_JOB",
                "req-1",
                "trace-1",
                user("*")
        );
        assertThat(service.evaluate(mixedSubjectRequest).reasonCode())
                .isEqualTo("SYSTEM_JOB_SUBJECT_CONFLICT");
    }

    @Test
    void requireAllowsAuthorizedRequest() {
        assertThatCode(() -> service(allowGrant("unused", "LOW", false, false))
                .require(AuthorizationRequest.permission(user("*"), "system:user:view"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> service(allowGrant("unused", "LOW", false, false))
                .require(AuthorizationRequest.permission(user("system:user:view"), "system:user:delete")))
                .isInstanceOf(BizException.class);
    }

    private DefaultAuthorizationService service(AgentToolGrantDecision decision) {
        return new DefaultAuthorizationService(request -> decision,
                request -> DelegationGrantDecision.allow("RESOURCE", "CRITICAL", false, false, List.of("TEST_DELEGATION")));
    }

    private AgentToolGrantDecision allowGrant(String permissionKey, String maxRisk, boolean confirm, boolean approval) {
        return AgentToolGrantDecision.allow("EXECUTE", permissionKey, maxRisk, confirm, approval, List.of("TEST_GRANT"));
    }

    private CurrentUser user(String permission) {
        CurrentUser currentUser = new CurrentUser(100L, "admin", "session-1", 1, true, Set.of(permission));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private AuthorizationRequest request(String channel, CurrentUser user, String permissionKey,
                                         String resource, String tool, String action) {
        return request(channel, user, permissionKey, resource, tool, action, Map.of());
    }

    private AuthorizationRequest request(String channel, CurrentUser user, String permissionKey,
                                         String resource, String tool, String action, Map<String, Object> arguments) {
        return new AuthorizationRequest(null, null, user == null ? null : user.getUserId(),
                user == null ? null : user.getUserUuid(), null,
                resource, action, permissionKey, tool, "LOW", null, arguments, false, false, channel,
                "req-1", "trace-1", user);
    }

    private AuthorizationRequest aiRequest(CurrentUser user, Long employeeId, String toolCode, String permissionKey,
                                           String riskLevel, boolean confirmed, boolean approvalGranted,
                                           Map<String, Object> arguments) {
        return AuthorizationRequest.aiTool(user, employeeId, toolCode, permissionKey, riskLevel, confirmed, approvalGranted, arguments);
    }
}
