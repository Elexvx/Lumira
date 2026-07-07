package com.lumira.saas.modules.activity.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityManagementAppServiceTest {

    @Test
    void createActivityShouldRequireCreatePermissionAtServiceLayer() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        assertThatThrownBy(() -> service.createActivity(user("aiadc:activity:view"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createActivityShouldRejectBlankUsernameBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        assertThatThrownBy(() -> service.createActivity(blankUsernameUser(), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createActivityShouldRejectMissingSessionIdBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        assertThatThrownBy(() -> service.createActivity(missingSessionIdUser(), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createActivityShouldRejectWhenLiveSnapshotRevokesCreatePermissionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("aiadc:activity:view")));
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations, permissionSnapshotService);

        assertThatThrownBy(() -> service.createActivity(user("aiadc:activity:create"), request()))
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
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("aiadc:activity:view")));
        ActivityManagementAppService service =
                new ActivityManagementAppService(queryOperations, permissionSnapshotService, systemInternalApi, null);
        CurrentUser currentUser = user("aiadc:activity:view");
        currentUser.setSimulatedRoleId(0L);
        Method method = ActivityManagementAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(2001L, "user-uuid-2001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    @Test
    void createActivityShouldRejectDisabledTrustedIdentityBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "operator-live", "DISABLED"));
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations, permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.createActivity(user("aiadc:activity:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        verify(permissionSnapshotService, never()).isTrustedActiveUser(2001L, "user-uuid-2001");
    }

    @Test
    void createActivityShouldRejectTrustedIdentityWhenLiveUsernameIsUnavailableBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", " ", "ENABLED"));
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations, permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.createActivity(user("aiadc:activity:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
        verify(permissionSnapshotService, never()).isTrustedActiveUser(2001L, "user-uuid-2001");
    }

    @Test
    void createActivityShouldRefreshLiveUsernameBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.countResult = 11L;
        queryOperations.rows = List.of(activityRow());
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "operator-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("aiadc:activity:create", "aiadc:activity:view")));
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations, permissionSnapshotService, systemInternalApi, null);
        CurrentUser currentUser = user("*");
        currentUser.setUsername("operator-stale");

        service.createActivity(currentUser, request());

        assertThat(currentUser.getUsername()).isEqualTo("operator-live");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
    }

    @Test
    void createActivityShouldRejectRevokedSessionTicketBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 2001L, "user-uuid-2001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        ActivityManagementAppService service =
                new ActivityManagementAppService(queryOperations, null, sessionAuthenticationService);

        assertThatThrownBy(() -> service.createActivity(user("aiadc:activity:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createActivityShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ActivityManagementAppService service =
                new ActivityManagementAppService(queryOperations, null, (SessionAuthenticationService) null);

        assertThatThrownBy(() -> service.createActivity(user("aiadc:activity:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void updateActivityShouldRequireUpdatePermissionBeforeLookup() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        assertThatThrownBy(() -> service.updateActivity(user("aiadc:activity:create"), 1L, request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void activityWritesShouldRejectNullRequestOrInvalidIdBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        assertThatThrownBy(() -> service.createActivity(user("aiadc:activity:create"), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.updateActivity(user("aiadc:activity:update"), 0L, request()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.deleteActivity(user("aiadc:activity:delete"), -1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void deleteActivityShouldRequireDeletePermissionAtServiceLayer() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        assertThatThrownBy(() -> service.deleteActivity(user("aiadc:activity:update"), 1L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void activityWritesShouldRejectUnsafeUrlsAndOversizedFieldsBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);
        ActivityDTO.ActivityUpsertRequest unsafeUrl = request();
        unsafeUrl.setImageUrl("javascript:alert(1)");
        ActivityDTO.ActivityUpsertRequest oversizedLocation = request();
        oversizedLocation.setLocation("x".repeat(256));

        assertThatThrownBy(() -> service.createActivity(user("aiadc:activity:create"), unsafeUrl))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createActivity(user("aiadc:activity:create"), oversizedLocation))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThat(queryOperations.updateCallCount).isZero();
        assertThat(queryOperations.queryCallCount).isZero();
    }

    @Test
    void createActivityShouldWriteTrustedUserUuidWithNumericAuditFields() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.countResult = 11L;
        queryOperations.rows = List.of(activityRow());
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        ActivityVO.Activity activity = service.createActivity(user("*"), request());

        assertThat(activity.getId()).isEqualTo(11L);
        assertThat(queryOperations.lastUpdateSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(queryOperations.lastUpdateArgs).contains(2001L, "user-uuid-2001");
    }

    @Test
    void createActivityShouldRejectWhenInsertMissesBeforeGeneratedIdLookup() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.updateCount = 0;
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        assertThatThrownBy(() -> service.createActivity(user("*"), request()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Activity changed, please retry");
                });

        assertThat(queryOperations.lastInsertIdQueries).isZero();
    }

    @Test
    void updateActivityShouldBindOriginalCodeLocaleAndStatusInFinalWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.rows = List.of(activityRow());
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        service.updateActivity(user("*"), 11L, request());

        assertThat(queryOperations.lastUpdateSql)
                .contains("where id = ? and code = ? and locale = ? and status = ? and deleted = 0");
        assertThat(queryOperations.lastUpdateArgs).containsSubsequence(11L, "internal-code", "zh", "published");
    }

    @Test
    void deleteActivityShouldBindOriginalCodeLocaleAndStatusInFinalWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.rows = List.of(activityRow());
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        service.deleteActivity(user("aiadc:activity:delete"), 11L);

        assertThat(queryOperations.lastUpdateSql)
                .contains("where id = ? and code = ? and locale = ? and status = ? and deleted = 0");
        assertThat(queryOperations.lastUpdateArgs).containsSubsequence(11L, "internal-code", "zh", "published");
    }

    @Test
    void publicPublishedActivitiesShouldRedactManagementFields() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.countResult = 1L;
        queryOperations.rows = List.of(activityRow());
        ActivityManagementAppService service = new ActivityManagementAppService(queryOperations);

        ActivityVO.PublicActivity activity = service.listPublishedActivities(null, null, null, 1L, 10L)
                .getRecords()
                .get(0);

        assertThat(activity.getTitle()).isEqualTo("Roadshow");
        assertThat(activity.getCtaHref()).isEqualTo("https://example.test");
        Set<String> publicFields = Arrays.stream(ActivityVO.PublicActivity.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertThat(publicFields).doesNotContain("code", "status", "sort", "createdAt", "updatedAt");
    }

    private static Map<String, Object> activityRow() {
        return Map.ofEntries(
                Map.entry("id", 11L),
                Map.entry("code", "internal-code"),
                Map.entry("locale", "zh"),
                Map.entry("title", "Roadshow"),
                Map.entry("subtitle", "Sub"),
                Map.entry("description", "Desc"),
                Map.entry("image_url", "/uploads/a.png"),
                Map.entry("sort", 1),
                Map.entry("status", "published"),
                Map.entry("tags", "featured"),
                Map.entry("cta_label", "Join"),
                Map.entry("cta_href", "https://example.test"),
                Map.entry("activity_date", "2026-07-03"),
                Map.entry("activity_time", "10:00"),
                Map.entry("location", "Shanghai"),
                Map.entry("featured", 1),
                Map.entry("created_at", LocalDateTime.now().minusDays(1)),
                Map.entry("updated_at", LocalDateTime.now())
        );
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
        CurrentUser currentUser = user("aiadc:activity:create");
        currentUser.setUsername(" ");
        return currentUser;
    }

    private static CurrentUser missingSessionIdUser() {
        CurrentUser currentUser = user("aiadc:activity:create");
        currentUser.setSessionId(null);
        return currentUser;
    }

    private static ActivityDTO.ActivityUpsertRequest request() {
        ActivityDTO.ActivityUpsertRequest request = new ActivityDTO.ActivityUpsertRequest();
        request.setTitle("Activity");
        request.setLocale("zh");
        request.setStatus("draft");
        request.setActivityDate("2026-07-03");
        request.setActivityTime("10:00");
        request.setLocation("Shanghai");
        return request;
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
        private String lastUpdateSql;
        private List<Object> lastUpdateArgs = List.of();
        private Long countResult = 0L;
        private int updateCount = 1;
        private int lastInsertIdQueries;
        private List<Map<String, Object>> rows = List.of();

        @Override
        public int update(String sql, Object... args) {
            updateCallCount += 1;
            lastUpdateSql = sql;
            lastUpdateArgs = Arrays.asList(args);
            return updateCount;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCallCount += 1;
            return rows.stream().map(row -> {
                try {
                    return rowMapper.mapRow(new SqlRow(row), 0);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }).toList();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("last_insert_id")) {
                lastInsertIdQueries += 1;
            }
            if (requiredType == Long.class) {
                return requiredType.cast(countResult);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryCallCount += 1;
            return List.of();
        }
    }
}
