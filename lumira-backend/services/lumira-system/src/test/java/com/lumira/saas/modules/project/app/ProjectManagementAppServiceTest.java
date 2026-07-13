package com.lumira.saas.modules.project.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.project.dto.ProjectDTO;
import com.lumira.saas.modules.project.infrastructure.JdbcProjectRepository;
import com.lumira.saas.modules.project.repository.ProjectRepository;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectManagementAppServiceTest {

    private static ProjectRepository repository(RecordingQueryOperations queryOperations) {
        return new JdbcProjectRepository(queryOperations);
    }

    @Test
    void createProjectShouldRequireCreateOrRegistrationCreatePermissionAtServiceLayer() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:view"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void createProjectShouldRejectBlankUsernameBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        assertThatThrownBy(() -> service.createProject(blankUsernameUser(), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createProjectShouldRejectMissingSessionVersionBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        assertThatThrownBy(() -> service.createProject(missingSessionVersionUser(), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createProjectShouldRejectMissingUserUuidBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));
        CurrentUser currentUser = user("aiadc:project:create");
        currentUser.setUserUuid(null);

        assertThatThrownBy(() -> service.createProject(currentUser, request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createProjectShouldRejectWhenLiveSnapshotRevokesCreatePermissionBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("aiadc:project:view")));
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations), permissionSnapshotService);

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "operator-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("aiadc:project:view")));
        ProjectManagementAppService service =
                new ProjectManagementAppService(repository(queryOperations), permissionSnapshotService, systemInternalApi, null);
        CurrentUser currentUser = user("aiadc:project:view");
        currentUser.setSimulatedRoleId(0L);
        Method method = ProjectManagementAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(2001L, "user-uuid-2001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    @Test
    void createProjectShouldRejectDisabledTrustedIdentityBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "operator-live", "DISABLED"));
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations), permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        verify(permissionSnapshotService, never()).isTrustedActiveUser(2001L, "user-uuid-2001");
    }

    @Test
    void createProjectShouldRejectBlankLiveUsernameBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", " ", "ENABLED"));
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations), permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        verify(permissionSnapshotService, never()).isTrustedActiveUser(2001L, "user-uuid-2001");
    }

    @Test
    void createProjectShouldRefreshLiveUsernameBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.rows = List.of(Map.ofEntries(
                Map.entry("id", 1L),
                Map.entry("code", "project-001"),
                Map.entry("locale", "zh"),
                Map.entry("title", "Project"),
                Map.entry("category", "demo"),
                Map.entry("rating", "popular"),
                Map.entry("sort", 100),
                Map.entry("status", "draft"),
                Map.entry("featured", 0)
        ));
        queryOperations.lastInsertId = 1L;
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", " operator-live ", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("aiadc:project:create", "aiadc:project:view")));
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations), permissionSnapshotService, systemInternalApi, null);
        CurrentUser currentUser = user("*");
        currentUser.setUsername("operator-stale");

        service.createProject(currentUser, request());

        assertThat(currentUser.getUsername()).isEqualTo("operator-live");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
    }

    @Test
    void createProjectShouldRejectRevokedSessionTicketBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 2001L, "user-uuid-2001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        ProjectManagementAppService service =
                new ProjectManagementAppService(repository(queryOperations), null, sessionAuthenticationService);

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createProjectShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service =
                new ProjectManagementAppService(repository(queryOperations), null, (SessionAuthenticationService) null);

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createProjectShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(null);
        ProjectManagementAppService service =
                new ProjectManagementAppService(repository(queryOperations), permissionSnapshotService, null, null);

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });

        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void deleteProjectShouldRejectMissingPermissionsVersionBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));
        CurrentUser currentUser = user("aiadc:project:delete");
        currentUser.setPermissionsVersion(null);

        assertThatThrownBy(() -> service.deleteProject(currentUser, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createProjectShouldAcceptRegistrationCreatePermission() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.rows = List.of(Map.ofEntries(
                Map.entry("id", 1L),
                Map.entry("code", "project-001"),
                Map.entry("locale", "zh"),
                Map.entry("title", "Project"),
                Map.entry("category", "demo"),
                Map.entry("rating", "popular"),
                Map.entry("sort", 100),
                Map.entry("status", "draft"),
                Map.entry("featured", 0)
        ));
        queryOperations.lastInsertId = 1L;
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        assertThat(service.createProject(user("aiadc:registration:create"), request()).getId()).isEqualTo(1L);
        assertThat(queryOperations.updateCallCount).isEqualTo(1);
        assertThat(queryOperations.lastUpdateSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(queryOperations.lastUpdateArgs).contains("user-uuid-2001");
    }

    @Test
    void createProjectShouldRejectWhenInsertMissesBeforeGeneratedIdLookup() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.updateCount = 0;
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Project changed, please retry");
                });

        assertThat(queryOperations.lastInsertIdQueries).isZero();
    }

    @Test
    void updateProjectShouldRequireUpdatePermissionBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        assertThatThrownBy(() -> service.updateProject(user("aiadc:project:create"), 1L, request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void projectWritesShouldRejectNullRequestOrInvalidIdBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.updateProject(user("aiadc:project:update"), 0L, request()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.deleteProject(user("aiadc:project:delete"), -1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void updateProjectShouldVerifyResourceExistsBeforeWriting() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        assertThatThrownBy(() -> service.updateProject(user("aiadc:project:update"), 1L, request()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        assertThat(queryOperations.queryCallCount).isEqualTo(1);
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void projectWritesShouldRejectUnsafeUrlsAndOversizedFieldsBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));
        ProjectDTO.ProjectUpsertRequest unsafeUrl = request();
        unsafeUrl.setCtaHref("javascript:alert(1)");
        ProjectDTO.ProjectUpsertRequest oversizedTitle = request();
        oversizedTitle.setTitle("x".repeat(129));

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), unsafeUrl))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), oversizedTitle))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void deleteProjectShouldRequireDeletePermissionAtServiceLayer() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        assertThatThrownBy(() -> service.deleteProject(user("aiadc:project:update"), 1L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void updateProjectShouldBindOriginalCodeLocaleAndStatusInFinalWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.rows = List.of(projectRow());
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        service.updateProject(user("*"), 1L, request());

        assertThat(queryOperations.lastUpdateSql)
                .contains("where id = ? and code = ? and locale = ? and status = ? and deleted = 0");
        assertThat(queryOperations.lastUpdateArgs).containsSubsequence(1L, "project-001", "zh", "published");
    }

    @Test
    void deleteProjectShouldBindOriginalCodeLocaleAndStatusInFinalWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.rows = List.of(projectRow());
        ProjectManagementAppService service = new ProjectManagementAppService(repository(queryOperations));

        service.deleteProject(user("aiadc:project:delete"), 1L);

        assertThat(queryOperations.lastUpdateSql)
                .contains("where id = ? and code = ? and locale = ? and status = ? and deleted = 0");
        assertThat(queryOperations.lastUpdateArgs).containsSubsequence(1L, "project-001", "zh", "published");
    }

    private static CurrentUser user(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUsername("operator");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private static CurrentUser blankUsernameUser() {
        CurrentUser currentUser = user("aiadc:project:create");
        currentUser.setUsername(" ");
        return currentUser;
    }

    private static CurrentUser missingSessionVersionUser() {
        CurrentUser currentUser = user("aiadc:project:create");
        currentUser.setSessionVersion(null);
        return currentUser;
    }

    private static ProjectDTO.ProjectUpsertRequest request() {
        ProjectDTO.ProjectUpsertRequest request = new ProjectDTO.ProjectUpsertRequest();
        request.setCode("project-001");
        request.setTitle("Project");
        request.setCategory("demo");
        request.setLocale("zh");
        request.setRating("popular");
        request.setStatus("draft");
        return request;
    }

    private static Map<String, Object> projectRow() {
        return Map.ofEntries(
                Map.entry("id", 1L),
                Map.entry("code", "project-001"),
                Map.entry("locale", "zh"),
                Map.entry("title", "Project"),
                Map.entry("category", "demo"),
                Map.entry("rating", "popular"),
                Map.entry("sort", 100),
                Map.entry("status", "published"),
                Map.entry("featured", 0)
        );
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

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private int updateCallCount;
        private int queryCallCount;
        private Long lastInsertId;
        private int lastInsertIdQueries;
        private int updateCount = 1;
        private String lastUpdateSql;
        private List<Object> lastUpdateArgs = List.of();
        private List<Map<String, Object>> rows = List.of();

        @Override
        public int update(String sql, Object... args) {
            updateCallCount += 1;
            lastUpdateSql = sql;
            lastUpdateArgs = Arrays.asList(args);
            return updateCount;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("last_insert_id")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(lastInsertId);
            }
            return null;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCallCount += 1;
            return rows.stream().map(row -> {
                try {
                    return rowMapper.mapRow(new com.lumira.saas.infrastructure.persistence.mybatis.SqlRow(row), 0);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).toList();
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryCallCount += 1;
            if (args.length > 0 && "aiadc_project_locale".equals(args[0])) {
                return List.of(Map.of("itemValue", "zh"), Map.of("itemValue", "en"));
            }
            if (args.length > 0 && "aiadc_project_status".equals(args[0])) {
                return List.of(Map.of("itemValue", "draft"), Map.of("itemValue", "published"));
            }
            if (args.length > 0 && "aiadc_project_rating".equals(args[0])) {
                return List.of(Map.of("itemValue", "popular"), Map.of("itemValue", "excellent"), Map.of("itemValue", "new"));
            }
            if (args.length > 0 && "aiadc_project_filter_all".equals(args[0])) {
                return List.of(Map.of("itemValue", "all"));
            }
            return List.of();
        }
    }
}
