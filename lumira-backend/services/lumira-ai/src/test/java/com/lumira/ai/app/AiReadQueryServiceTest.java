package com.lumira.ai.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiReadQueryServiceTest {

    @Test
    void listEmployeesUsesBoundedPageAndCappedCount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), anyRowMapper(), anyVarargs())).thenReturn(List.of());
        AiReadQueryService service = new AiReadQueryService(jdbcTemplate);

        var response = service.listEmployees(user(Set.of("ai:view")), 0, 500);

        assertThat(response.getPageNo()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(100);
        assertThat(response.getTotal()).isZero();
        ArgumentCaptor<Object> limitCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> offsetCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).query(anyString(), anyRowMapper(), limitCaptor.capture(), offsetCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(101L);
        assertThat(offsetCaptor.getValue()).isEqualTo(0L);
    }

    @Test
    void listEmployeesRequiresAuthenticatedUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService service = new AiReadQueryService(jdbcTemplate);

        assertThatThrownBy(() -> service.listEmployees(null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listEmployeesRejectsUnauthenticatedUserBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService service = new AiReadQueryService(jdbcTemplate);

        assertThatThrownBy(() -> service.listEmployees(unauthenticatedUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listEmployeesRejectsBlankUsernameBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService service = new AiReadQueryService(jdbcTemplate);

        assertThatThrownBy(() -> service.listEmployees(blankUsernameUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listEmployeesRejectsMissingSessionIdBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService service = new AiReadQueryService(jdbcTemplate);

        assertThatThrownBy(() -> service.listEmployees(missingSessionIdUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listEmployeesRejectsDisabledTrustedUserBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, "ai-user", "DISABLED"));
        AiReadQueryService service = new AiReadQueryService(jdbcTemplate, provider(systemInternalApi));

        assertThatThrownBy(() -> service.listEmployees(user(Set.of("ai:view")), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listToolsExposesOnlyToolsVisibleToCurrentUser() {
        AiReadQueryService service = new AiReadQueryService(mock(JdbcTemplate.class));

        var tools = service.listTools(user(Set.of("system:permission:snapshot", "system:file:view")));

        assertThat(tools)
                .extracting(tool -> tool.toolCode())
                .contains("system.permission.snapshot", "file.object.search")
                .doesNotContain("system.user.create");
    }

    @Test
    void listToolsRejectsUnauthenticatedPermissionSnapshot() {
        AiReadQueryService service = new AiReadQueryService(mock(JdbcTemplate.class));
        CurrentUser currentUser = user(Set.of("*"));
        currentUser.setAuthenticated(false);

        assertThatThrownBy(() -> service.listTools(currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void allToolsKeepsInternalCatalogForExecutionPermissionChecks() {
        AiReadQueryService service = new AiReadQueryService(mock(JdbcTemplate.class));

        var tools = service.allTools();

        assertThat(tools)
                .anySatisfy(tool -> {
                    assertThat(tool.toolCode()).isEqualTo("system.user.create");
                    assertThat(tool.needConfirm()).isTrue();
                    assertThat(tool.readOnly()).isFalse();
                });
    }

    @Test
    void listToolsRequiresAuthenticatedUser() {
        AiReadQueryService service = new AiReadQueryService(mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.listTools(null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void listConversationMessagesUsesExistsProbeInsteadOfCount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("select 1"), anyVarargs()))
                .thenReturn(List.of(java.util.Map.of("exists", 1)));
        when(jdbcTemplate.query(anyString(), anyRowMapper(), anyVarargs())).thenReturn(List.of());
        AiReadQueryService service = new AiReadQueryService(jdbcTemplate);

        var messages = service.listConversationMessages(user(Set.of("ai:conversation:view")), 99L);

        assertThat(messages).isEmpty();
        verify(jdbcTemplate).queryForList(contains("select 1"), anyVarargs());
        verify(jdbcTemplate, never()).queryForObject(contains("count(1)"), org.mockito.ArgumentMatchers.<Class<Integer>>any(), anyVarargs());
    }

    @Test
    void requireManageableKnowledgeBaseShouldExcludePlatformVisibilityAndUseManageAclOnly() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), anyRowMapper(), anyVarargs())).thenReturn(List.of());
        AiReadQueryService service = new AiReadQueryService(jdbcTemplate);

        assertThatThrownBy(() -> service.requireManageableKnowledgeBase(sharedManager(), 11L))
                .isInstanceOf(BizException.class);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), anyRowMapper(), anyVarargs());
        assertThat(sqlCaptor.getValue())
                .contains("kb.owner_user_id = ?")
                .contains("kb.owner_user_uuid = ?")
                .contains("acl.permission in (?)")
                .contains("acl.subject_type = 'ROLE'")
                .contains("acl.subject_type = 'DEPARTMENT'")
                .doesNotContain("kb.visibility_scope = ?")
                .doesNotContain("acl.permission in (?,?,?)");
    }

    @SuppressWarnings("unchecked")
    private <T> RowMapper<T> anyRowMapper() {
        return org.mockito.ArgumentMatchers.any(RowMapper.class);
    }

    private Object[] anyVarargs() {
        return org.mockito.ArgumentMatchers.any(Object[].class);
    }

    private CurrentUser user(Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser(7L, "ai-user", 2002L, "s1", 1, true, permissions);
        currentUser.setUserUuid("user-uuid-7");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private CurrentUser sharedManager() {
        CurrentUser currentUser = user(Set.of("ai:knowledge:manage"));
        currentUser.setRoleIds(Set.of(9L));
        currentUser.setPrimaryDeptId(15L);
        currentUser.setDeptIds(Set.of(18L));
        return currentUser;
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(7L, "ai-user", 2002L, "s1", 1, false, Set.of("*"));
    }

    private CurrentUser blankUsernameUser() {
        return new CurrentUser(7L, " ", 2002L, "s1", 1, true, Set.of("*"));
    }

    private CurrentUser missingSessionIdUser() {
        return new CurrentUser(7L, "ai-user", 2002L, null, 1, true, Set.of("*"));
    }

    private ObjectProvider<SystemInternalApi> provider(SystemInternalApi systemInternalApi) {
        if (systemInternalApi != null) {
            when(systemInternalApi.permissionSnapshot(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> permissionSnapshot(invocation.getArgument(0, Long.class)));
        }
        ObjectProvider<SystemInternalApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(systemInternalApi);
        return provider;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private PermissionSnapshotDTO permissionSnapshot(Long userId) {
        return new PermissionSnapshotDTO(
                "perm-v" + userId,
                List.of("ai:view"),
                List.of(11L),
                21L,
                List.of(21L),
                List.of(21L, 22L),
                List.of(),
                "/ai"
        );
    }
}
