package com.lumira.saas.modules.ai.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiToolOrchestrationServiceHashTest {

    @Test
    void proposeShouldRejectUnauthenticatedUserBeforePlanning() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));

        assertThatThrownBy(() -> service.propose(unauthenticatedUser(), propose(Map.of("keyword", "admin"))))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbc.insertSql).isNull();
    }

    @Test
    void proposeShouldRejectBlankUsernameBeforePlanning() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));

        assertThatThrownBy(() -> service.propose(blankUsernameUser(), propose(Map.of("keyword", "admin"))))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbc.insertSql).isNull();
    }

    @Test
    void proposeShouldRejectMissingSessionVersionBeforePlanning() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));

        assertThatThrownBy(() -> service.propose(missingSessionVersionUser(), propose(Map.of("keyword", "admin"))))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbc.insertSql).isNull();
    }

    @Test
    void proposeShouldRejectMissingUserUuidBeforePlanning() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        CurrentUser currentUser = currentUser();
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> service.propose(currentUser, propose(Map.of("keyword", "admin"))))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbc.insertSql).isNull();
    }

    @Test
    void confirmShouldRejectMissingPermissionsVersionBeforeLoadingPlan() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion(" ");

        assertThatThrownBy(() -> service.confirm(currentUser, confirm(501L)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbc.planLoadCount).isZero();
    }

    @Test
    void confirmShouldRejectUnauthenticatedUserBeforeLoadingPlan() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));

        assertThatThrownBy(() -> service.confirm(unauthenticatedUser(), confirm(501L)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbc.planLoadCount).isZero();
    }

    @Test
    void confirmShouldRejectRevokedSessionTicketBeforeLoadingPlan() {
        StubQueryOperations jdbc = new StubQueryOperations();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        DefaultAiToolOrchestrationService service = service(
                jdbc,
                new MutableAuthorizationService(request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow")),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(501L)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(jdbc.planLoadCount).isZero();
    }

    @Test
    void proposeShouldRejectTrustedUserWhenNoTrustedResolverIsAvailable() {
        StubQueryOperations jdbc = new StubQueryOperations();
        AiNativeToolRuntimeService runtimeService = mock(AiNativeToolRuntimeService.class);
        when(runtimeService.listTools(any(), anyLong())).thenReturn(List.of(tool()));
        DefaultAiToolOrchestrationService service = new DefaultAiToolOrchestrationService(
                jdbc,
                new ObjectMapper(),
                runtimeService,
                mock(AiToolPolicyService.class),
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                new PermissionGuard(new MutableAuthorizationService(request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"))),
                new MutableAuthorizationService(request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow")),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.propose(currentUser(), propose(Map.of("keyword", "admin"))))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbc.insertSql).isNull();
    }

    @Test
    void proposeShouldUseLivePermissionSnapshotBeforePlanning() {
        StubQueryOperations jdbc = new StubQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of()));
        DefaultAiToolOrchestrationService service = service(
                jdbc,
                request -> request.currentUser().getPermissions().contains("system:user:view")
                        ? AuthorizationDecision.allow("AUTHZ_ALLOW", "allow")
                        : AuthorizationDecision.deny("AUTHZ_DENY", "revoked by live snapshot"),
                permissionSnapshotService
        );

        assertThatThrownBy(() -> service.propose(currentUser(), propose(Map.of("keyword", "admin"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("revoked by live snapshot");

        assertThat(jdbc.insertSql).isNull();
    }

    @Test
    void proposeShouldRejectDisabledTrustedUserBeforePlanning() {
        StubQueryOperations jdbc = new StubQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L)).thenReturn(userSnapshot(100L, "admin", "DISABLED"));
        DefaultAiToolOrchestrationService service = service(
                jdbc,
                new MutableAuthorizationService(request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow")),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> service.propose(currentUser(), propose(Map.of("keyword", "admin"))))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbc.insertSql).isNull();
    }

    @Test
    void proposeShouldRejectTrustedUserWhenLiveUsernameIsUnavailableBeforePlanning() {
        StubQueryOperations jdbc = new StubQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L)).thenReturn(userSnapshot(100L, " ", "ENABLED"));
        DefaultAiToolOrchestrationService service = service(
                jdbc,
                new MutableAuthorizationService(request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow")),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> service.propose(currentUser(), propose(Map.of("keyword", "admin"))))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbc.insertSql).isNull();
        org.mockito.Mockito.verify(permissionSnapshotService, org.mockito.Mockito.never()).isTrustedActiveUser(100L, "user-uuid-100");
    }

    @Test
    void proposeShouldRefreshTrustedUsernameFromLiveIdentityBeforePlanning() {
        StubQueryOperations jdbc = new StubQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L)).thenReturn(userSnapshot(100L, "live-admin", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:user:view")));
        DefaultAiToolOrchestrationService service = service(
                jdbc,
                new MutableAuthorizationService(request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow")),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = currentUser();

        service.propose(currentUser, propose(Map.of("keyword", "admin")));

        assertThat(currentUser.getUsername()).isEqualTo("live-admin");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
    }

    @Test
    void proposeWritesArgumentsHashAndAuthorizationSnapshot() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));

        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin", "limit", 10)));

        assertThat(plan.getArgumentsHash()).hasSize(64);
        assertThat(plan.getAuthorizationSnapshotJson())
                .contains("\"ownerUserUuid\":\"user-uuid-100\"")
                .contains("\"toolCode\":\"system.user.search\"")
                .contains("\"authorizationVerdict\":\"ALLOW\"");
        assertThat(jdbc.insertSql).contains("owner_user_uuid", "arguments_hash", "authorization_snapshot_json", "approval_required");
        assertThat(jdbc.insertArgs[3]).isEqualTo("user-uuid-100");
        assertThat(jdbc.insertArgs[16]).isEqualTo(plan.getArgumentsHash());
        assertThat(jdbc.insertArgs[17]).isEqualTo(plan.getAuthorizationSnapshotJson());
        assertThat(jdbc.insertArgs[18]).isEqualTo(0);
    }

    @Test
    void proposeShouldPersistSimulatedRoleIdInAuthorizationSnapshot() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));

        AiVO.ToolPlanVO plan = service.propose(currentUserWithSimulatedRole(9L), propose(Map.of("keyword", "admin")));

        assertThat(plan.getAuthorizationSnapshotJson()).contains("\"simulatedRoleId\":9");
        assertThat(jdbc.insertArgs[17]).isEqualTo(plan.getAuthorizationSnapshotJson());
    }

    @Test
    void proposeUsesViewActionForReadOnlyTool() {
        StubQueryOperations jdbc = new StubQueryOperations();
        MutableAuthorizationService authorization = new MutableAuthorizationService(request -> {
            if ("system.user.search".equals(request.toolCode())) {
                assertThat(request.actionCode()).isEqualTo("view");
            }
            return AuthorizationDecision.allow("AUTHZ_ALLOW", "allow");
        });
        DefaultAiToolOrchestrationService service = service(jdbc, authorization);
        AiDTO.ToolProposeRequest request = new AiDTO.ToolProposeRequest();
        request.setEmployeeId(300L);
        request.setToolCode("system.user.search");
        request.setMessage("search users");
        request.setArguments(Map.of());

        AiVO.ToolPlanVO plan = service.propose(currentUser(), request);

        assertThat(plan.getToolCode()).isEqualTo("system.user.search");
    }

    @Test
    void confirmUsesViewActionForReadOnlyTool() {
        StubQueryOperations jdbc = new StubQueryOperations();
        MutableAuthorizationService authorization = new MutableAuthorizationService(request -> {
            if ("system.user.search".equals(request.toolCode()) && request.confirmed()) {
                assertThat(request.actionCode()).isEqualTo("view");
            }
            return AuthorizationDecision.allow("AUTHZ_ALLOW", "allow");
        });
        DefaultAiToolOrchestrationService service = service(jdbc, authorization);
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));

        service.confirm(currentUser(), confirm(plan.getId()));
    }

    @Test
    void proposeRejectsWhenPlanInsertMissesBeforeGeneratedIdLookup() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.planInsertUpdates = 0;
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));

        assertThatThrownBy(() -> service.propose(currentUser(), propose(Map.of("keyword", "admin"))))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI tool plan changed, please retry");
                });

        assertThat(jdbc.lastInsertIdQueries).isZero();
    }

    @Test
    void confirmAllowsMatchingHashAndClaimsPendingPlanOnce() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));

        service.confirm(currentUser(), confirm(plan.getId()));

        assertThat(jdbc.executingClaims).isEqualTo(1);
        assertThat(jdbc.lastClaimSql).contains("owner_user_id = ?", "owner_user_uuid = ?");
        assertThat(jdbc.lastClaimArgs).containsSequence(501L, 100L, "user-uuid-100");
        assertThat(jdbc.lastStatusUpdateSql).contains(
                "owner_user_id = ?",
                "owner_user_uuid = ?",
                "status = ?",
                "arguments_hash = ?"
        );
        assertThat(jdbc.lastStatusUpdateArgs).containsSequence(501L, 100L, "user-uuid-100", "EXECUTING", plan.getArgumentsHash());
        assertThat(jdbc.lastAuditUpdateSql)
                .contains("owner_user_id = ?", "owner_user_uuid = ?", "conversation_id <=> ?", "employee_id <=> ?", "skill_code = ?");
        assertThat(jdbc.lastAuditUpdateArgs).containsSequence(100L, "user-uuid-100", 900L, 300L, "system.user.search");
        assertThat(jdbc.finalStatuses).contains("EXECUTED");
    }

    @Test
    void confirmRejectsTamperedArgumentsJson() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));
        plan.setArguments(Map.of("keyword", "tampered"));

        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("arguments were modified");
        assertThat(jdbc.finalStatuses).contains("BLOCKED");
    }

    @Test
    void confirmRejectsTamperedPlanMetadataEvenWhenArgumentsHashStillMatches() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));
        plan.setToolCode("system.user.delete");
        plan.setPermissionKey("system:user:delete");
        plan.setRiskLevel("HIGH");

        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("authorization snapshot is invalid");
        assertThat(jdbc.finalStatuses).contains("BLOCKED");
        assertThat(jdbc.executingClaims).isZero();
    }

    @Test
    void confirmRejectsWhenSimulatedRoleContextChangesAfterPropose() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        AiVO.ToolPlanVO plan = service.propose(currentUserWithSimulatedRole(9L), propose(Map.of("keyword", "admin")));

        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("authorization snapshot is invalid");
        assertThat(jdbc.finalStatuses).contains("BLOCKED");
        assertThat(jdbc.executingClaims).isZero();
    }

    @Test
    void confirmRejectsWhenAuthorizationChanges() {
        StubQueryOperations jdbc = new StubQueryOperations();
        MutableAuthorizationService authorization = new MutableAuthorizationService(request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        DefaultAiToolOrchestrationService service = service(jdbc, authorization);
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));
        authorization.evaluator = request -> AuthorizationDecision.deny("AUTHZ_DENY", "denied after propose");

        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("denied after propose");
        assertThat(jdbc.finalStatuses).contains("BLOCKED");
    }

    @Test
    void confirmShouldReauthorizeAgainstLivePermissionSnapshot() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService planner = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        AiVO.ToolPlanVO plan = planner.propose(currentUser(), propose(Map.of("keyword", "admin")));

        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of()));
        MutableAuthorizationService authorization = new MutableAuthorizationService(request ->
                request.currentUser().getPermissions().contains("system:user:view")
                        ? AuthorizationDecision.allow("AUTHZ_ALLOW", "allow")
                        : AuthorizationDecision.deny("AUTHZ_DENY", "revoked by live snapshot"));
        DefaultAiToolOrchestrationService confirmer = service(jdbc, authorization, permissionSnapshotService);

        assertThatThrownBy(() -> confirmer.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("revoked by live snapshot");
        assertThat(jdbc.finalStatuses).contains("BLOCKED");
    }

    @Test
    void confirmRejectsDuplicatePendingClaim() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.claimUpdates = 0;
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));

        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class);
        assertThat(jdbc.executingClaims).isZero();
    }

    @Test
    void confirmRejectsWhenFinalStatusWriteDoesNotMatchLoadedPlanSnapshot() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.statusUpdates = 0;
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));

        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI tool plan changed, please retry");
                });
        assertThat(jdbc.lastStatusUpdateSql).contains("status = ?", "arguments_hash = ?");
        assertThat(jdbc.lastStatusUpdateArgs).containsSequence(501L, 100L, "user-uuid-100", "EXECUTING", plan.getArgumentsHash());
    }

    @Test
    void confirmRejectsWhenApprovalRequiredButNotGranted() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc,
                request -> !"AI_AGENT".equals(request.channel())
                        ? AuthorizationDecision.allow("RBAC_ALLOW", "rbac allow")
                        : request.approvalGranted()
                        ? AuthorizationDecision.allow("AUTHZ_APPROVED", "approved")
                        : AuthorizationDecision.requireApproval("AUTHZ_APPROVAL", "approval required", List.of("AUTHZ_APPROVAL")));
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));

        assertThat(plan.getApprovalRequired()).isTrue();
        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("approval required");
    }

    private DefaultAiToolOrchestrationService service(StubQueryOperations jdbc, Function<AuthorizationRequest, AuthorizationDecision> evaluator) {
        return service(jdbc, new MutableAuthorizationService(evaluator), null, null);
    }

    private DefaultAiToolOrchestrationService service(StubQueryOperations jdbc, AuthorizationService authorizationService) {
        return service(jdbc, authorizationService, null, null);
    }

    private DefaultAiToolOrchestrationService service(
            StubQueryOperations jdbc,
            Function<AuthorizationRequest, AuthorizationDecision> evaluator,
            PermissionSnapshotService permissionSnapshotService
    ) {
        return service(jdbc, new MutableAuthorizationService(evaluator), permissionSnapshotService, null, null);
    }

    private DefaultAiToolOrchestrationService service(
            StubQueryOperations jdbc,
            AuthorizationService authorizationService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        return service(jdbc, authorizationService, permissionSnapshotService, null, null);
    }

    private DefaultAiToolOrchestrationService service(
            StubQueryOperations jdbc,
            AuthorizationService authorizationService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        return service(jdbc, authorizationService, permissionSnapshotService, null, sessionAuthenticationService);
    }

    private DefaultAiToolOrchestrationService service(
            StubQueryOperations jdbc,
            AuthorizationService authorizationService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        AiNativeToolRuntimeService runtimeService = mock(AiNativeToolRuntimeService.class);
        when(runtimeService.listTools(any(), anyLong())).thenReturn(List.of(tool()));
        AiVO.ToolExecuteResultVO executeResult = new AiVO.ToolExecuteResultVO();
        executeResult.setToolCode("system.user.search");
        executeResult.setResultStatus("SUCCESS");
        when(runtimeService.execute(any(), any())).thenReturn(executeResult);

        AiToolPolicyService policyService = mock(AiToolPolicyService.class);
        when(policyService.evaluate(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new AiToolPolicyService.PolicyDecision("ALLOW", "policy allow", List.of("POLICY_ALLOW")));

        AiLlmServiceConfigProvider configProvider = mock(AiLlmServiceConfigProvider.class);
        when(configProvider.findSupervisor()).thenReturn(Optional.empty());

        return new DefaultAiToolOrchestrationService(
                jdbc,
                new ObjectMapper(),
                runtimeService,
                policyService,
                configProvider,
                mock(AiChatModelFactory.class),
                new PermissionGuard(authorizationService),
                authorizationService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                false
        );
    }

    private AiVO.ToolVO tool() {
        AiVO.ToolVO tool = new AiVO.ToolVO();
        tool.setToolCode("system.user.search");
        tool.setToolName("Search users");
        tool.setRiskLevel("LOW");
        tool.setReadOnly(true);
        tool.setNeedConfirm(false);
        tool.setRequiredPermission("system:user:view");
        return tool;
    }

    private AiDTO.ToolProposeRequest propose(Map<String, Object> arguments) {
        AiDTO.ToolProposeRequest request = new AiDTO.ToolProposeRequest();
        request.setEmployeeId(300L);
        request.setConversationId(900L);
        request.setToolCode("system.user.search");
        request.setMessage("search users");
        request.setArguments(arguments);
        return request;
    }

    private AiDTO.ToolConfirmRequest confirm(Long planId) {
        AiDTO.ToolConfirmRequest request = new AiDTO.ToolConfirmRequest();
        request.setPendingToolCallId(planId);
        return request;
    }

    private CurrentUser currentUser() {
        return trusted(new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, Set.of("*", "system:user:view")));
    }

    private CurrentUser currentUserWithSimulatedRole(Long simulatedRoleId) {
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(simulatedRoleId);
        return currentUser;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(100L, "admin", 1001L, "session-1", 1, false, Set.of("*", "system:user:view"));
    }

    private CurrentUser blankUsernameUser() {
        return new CurrentUser(100L, " ", 1001L, "session-1", 1, true, Set.of("*", "system:user:view"));
    }

    private CurrentUser missingSessionVersionUser() {
        return new CurrentUser(100L, "admin", 1001L, "session-1", null, true, Set.of("*", "system:user:view"));
    }

    private CurrentUser trusted(CurrentUser currentUser) {
        currentUser.setUserUuid("user-uuid-" + currentUser.getUserId());
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private static class MutableAuthorizationService implements AuthorizationService {
        private Function<AuthorizationRequest, AuthorizationDecision> evaluator;

        private MutableAuthorizationService(Function<AuthorizationRequest, AuthorizationDecision> evaluator) {
            this.evaluator = evaluator;
        }

        @Override
        public AuthorizationDecision evaluate(AuthorizationRequest request) {
            return evaluator.apply(request);
        }

        @Override
        public void require(AuthorizationRequest request) {
            AuthorizationDecision decision = evaluate(request);
            if (!decision.allowed()) {
                throw new BizException(ErrorCode.FORBIDDEN, decision.message());
            }
        }
    }

    private static class StubQueryOperations extends MyBatisQueryOperations {
        private AiVO.ToolPlanVO plan;
        private String insertSql;
        private Object[] insertArgs;
        private int planInsertUpdates = 1;
        private int lastInsertIdQueries;
        private int claimUpdates = 1;
        private int statusUpdates = 1;
        private int executingClaims;
        private int planLoadCount;
        private String lastClaimSql;
        private Object[] lastClaimArgs;
        private String lastStatusUpdateSql;
        private Object[] lastStatusUpdateArgs;
        private String lastAuditUpdateSql;
        private Object[] lastAuditUpdateArgs;
        private final List<String> finalStatuses = new java.util.ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("insert into ai_tool_call_plan")) {
                insertSql = sql;
                insertArgs = args;
                plan = new AiVO.ToolPlanVO();
                plan.setId(501L);
                plan.setConversationId((Long) args[0]);
                plan.setEmployeeId((Long) args[1]);
                plan.setToolCode((String) args[4]);
                plan.setToolName((String) args[5]);
                plan.setActionType((String) args[6]);
                plan.setRiskLevel((String) args[7]);
                plan.setSummary((String) args[8]);
                plan.setPermissionKey((String) args[9]);
                plan.setRequiresConfirm(((Integer) args[10]) == 1);
                plan.setSupervisorVerdict((String) args[11]);
                plan.setSupervisorMessage((String) args[12]);
                plan.setPolicyVerdict((String) args[13]);
                plan.setPolicyMessage((String) args[14]);
                plan.setArguments(parseArguments((String) args[15]));
                plan.setArgumentsHash((String) args[16]);
                plan.setAuthorizationSnapshotJson((String) args[17]);
                plan.setApprovalRequired(((Integer) args[18]) == 1);
                plan.setStatus((String) args[19]);
                plan.setExpiresAt((LocalDateTime) args[20]);
                plan.setCreateTime((LocalDateTime) args[21]);
                return planInsertUpdates;
            }
            if (sql.contains("set status = 'EXECUTING'")) {
                lastClaimSql = sql;
                lastClaimArgs = args;
                if (claimUpdates == 1) {
                    executingClaims += 1;
                    plan.setStatus("EXECUTING");
                }
                return claimUpdates;
            }
            if (sql.contains("update ai_tool_call_plan") && sql.contains("set status = ?")) {
                lastStatusUpdateSql = sql;
                lastStatusUpdateArgs = args;
                finalStatuses.add((String) args[0]);
                if (statusUpdates == 1) {
                    plan.setStatus((String) args[0]);
                }
                return statusUpdates;
            }
            if (sql.contains("update ai_tool_audit_log")) {
                lastAuditUpdateSql = sql;
                lastAuditUpdateArgs = args;
                return 1;
            }
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("select id from ai_tool_call_plan") || sql.contains("last_insert_id")) {
                if (sql.contains("last_insert_id")) {
                    lastInsertIdQueries += 1;
                }
                return requiredType.cast(501L);
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from ai_tool_call_plan")) {
                planLoadCount += 1;
                return plan == null ? List.of() : List.of((T) plan);
            }
            return List.of();
        }

        private Map<String, Object> parseArguments(String json) {
            try {
                return new ObjectMapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
            } catch (Exception exception) {
                return Map.of();
            }
        }
    }
}
