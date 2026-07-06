package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiToolRegistryTest {

    @Test
    void listRegisteredSkills_shouldHideSkillDeniedByAuthorizationService() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser());
        when(authorizationService.evaluate(any(AuthorizationRequest.class)))
                .thenReturn(AuthorizationDecision.deny("RBAC_PERMISSION_MISSING", "Permission denied"));
        AiToolRegistry registry = new DefaultAiToolRegistry(
                new StaticSkillQueryOperations(skill("execute")),
                authorizationService,
                securityContextFacade
        );

        List<AiVO.SkillVO> skills = registry.listRegisteredSkills(3001L);

        assertThat(skills).isEmpty();
    }

    @Test
    void listRegisteredSkills_shouldExposeSkillAllowedByAuthorizationService() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser());
        when(authorizationService.evaluate(any(AuthorizationRequest.class)))
                .thenReturn(AuthorizationDecision.allow("AUTHZ_POLICY_ALLOW", "Permission granted"));
        AiToolRegistry registry = new DefaultAiToolRegistry(
                new StaticSkillQueryOperations(skill("execute")),
                authorizationService,
                securityContextFacade
        );

        List<AiVO.SkillVO> skills = registry.listRegisteredSkills(3001L);

        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getSkillCode()).isEqualTo("file.search");

        ArgumentCaptor<AuthorizationRequest> requestCaptor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService).evaluate(requestCaptor.capture());
        AuthorizationRequest request = requestCaptor.getValue();
        assertThat(request.humanUserId()).isEqualTo(2001L);
        assertThat(request.humanUserUuid()).isEqualTo("user-uuid-2001");
        assertThat(request.humanSubject().refId()).isEqualTo(2001L);
        assertThat(request.currentUser().getUserUuid()).isEqualTo("user-uuid-2001");
    }

    @Test
    void listRegisteredSkills_shouldHideSkillsForMissingSessionVersionBeforeAuthorization() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        AiToolRegistry registry = new DefaultAiToolRegistry(
                new StaticSkillQueryOperations(skill("execute")),
                authorizationService,
                securityContextFacade
        );

        List<AiVO.SkillVO> skills = registry.listRegisteredSkills(3001L);

        assertThat(skills).isEmpty();
    }

    @Test
    void listRegisteredSkills_shouldUseLivePermissionSnapshotDuringAuthorization() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser());
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of()));
        when(authorizationService.evaluate(any(AuthorizationRequest.class)))
                .thenAnswer(invocation -> {
                    AuthorizationRequest request = invocation.getArgument(0);
                    return request.currentUser().getPermissions().contains("ai:tool:file.search")
                            ? AuthorizationDecision.allow("AUTHZ_POLICY_ALLOW", "Permission granted")
                            : AuthorizationDecision.deny("RBAC_PERMISSION_MISSING", "Permission denied");
                });
        AiToolRegistry registry = new DefaultAiToolRegistry(
                new StaticSkillQueryOperations(skill("execute")),
                authorizationService,
                securityContextFacade,
                permissionSnapshotService
        );

        List<AiVO.SkillVO> skills = registry.listRegisteredSkills(3001L);

        assertThat(skills).isEmpty();
        ArgumentCaptor<AuthorizationRequest> requestCaptor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService).evaluate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().currentUser().getPermissionsVersion()).isEqualTo("permissions-2");
        assertThat(requestCaptor.getValue().currentUser().getPermissions()).isEmpty();
    }

    @Test
    void listRegisteredSkills_shouldRejectRevokedSessionTicketBeforeQuery() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        RecordingSkillQueryOperations queryOperations = new RecordingSkillQueryOperations(skill("execute"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser());
        when(sessionAuthenticationService.authenticateSessionTicket(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        AiToolRegistry registry = new DefaultAiToolRegistry(
                queryOperations,
                authorizationService,
                securityContextFacade,
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> registry.listRegisteredSkills(3001L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(queryOperations.queryCalled).isFalse();
    }

    @Test
    void listRegisteredSkills_shouldRejectDisabledTrustedIdentityBeforeQuery() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        RecordingSkillQueryOperations queryOperations = new RecordingSkillQueryOperations(skill("execute"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser());
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "alice-live", "DISABLED"));
        AiToolRegistry registry = new DefaultAiToolRegistry(
                queryOperations,
                authorizationService,
                securityContextFacade,
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> registry.listRegisteredSkills(3001L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(queryOperations.queryCalled).isFalse();
        verify(permissionSnapshotService, org.mockito.Mockito.never()).isTrustedActiveUser(2001L, "user-uuid-2001");
    }

    @Test
    void listRegisteredSkills_shouldRefreshLiveUsernameBeforeAuthorization() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername("alice-stale");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "alice-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("ai:tool:file.search")));
        when(authorizationService.evaluate(any(AuthorizationRequest.class)))
                .thenReturn(AuthorizationDecision.allow("AUTHZ_POLICY_ALLOW", "Permission granted"));
        AiToolRegistry registry = new DefaultAiToolRegistry(
                new StaticSkillQueryOperations(skill("execute")),
                authorizationService,
                securityContextFacade,
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        List<AiVO.SkillVO> skills = registry.listRegisteredSkills(3001L);

        assertThat(skills).hasSize(1);
        ArgumentCaptor<AuthorizationRequest> requestCaptor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService).evaluate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().currentUser().getUsername()).isEqualTo("alice-live");
        assertThat(requestCaptor.getValue().currentUser().getPermissionsVersion()).isEqualTo("permissions-2");
        assertThat(currentUser.getUsername()).isEqualTo("alice-live");
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser(2001L, "alice", 1001L, "session-1", 1, true, Set.of("ai:tool:*"));
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private AiVO.SkillVO skill(String permissionMode) {
        AiVO.SkillVO skill = new AiVO.SkillVO();
        skill.setId(9001L);
        skill.setSkillCode("file.search");
        skill.setSkillName("File Search");
        skill.setRiskLevel("LOW");
        skill.setReadOnly(false);
        skill.setNeedConfirm(false);
        skill.setPermissionMode(permissionMode);
        return skill;
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
                username,
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static class StaticSkillQueryOperations extends MyBatisQueryOperations {
        private final AiVO.SkillVO skill;

        StaticSkillQueryOperations(AiVO.SkillVO skill) {
            this.skill = skill;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return (List<T>) List.of(skill);
        }
    }

    private static final class RecordingSkillQueryOperations extends StaticSkillQueryOperations {
        private boolean queryCalled;

        RecordingSkillQueryOperations(AiVO.SkillVO skill) {
            super(skill);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCalled = true;
            return super.query(sql, rowMapper, args);
        }
    }
}
