package com.lumira.saas.modules.system.sensitive.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensitiveWordPluginStateServiceTest {

    @Test
    void isEnabledShouldUseGlobalPluginActivationAndTableChecks() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = true;
        queryOperations.tableExists = true;
        queryOperations.requiredColumnCount = 14L;
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations);

        assertThat(service.isEnabled(currentUser())).isTrue();
        assertThat(service.isEnabled(currentUser())).isTrue();
        assertThat(queryOperations.existsCallCount).isEqualTo(3);
        assertThat(queryOperations.countQueryCalled).isTrue();
    }

    @Test
    void ensureEnabledShouldRejectWhenPluginNotEnabled() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = false;
        queryOperations.tableExists = true;
        queryOperations.requiredColumnCount = 14L;
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations);

        assertThatThrownBy(() -> service.ensureEnabled(currentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.PLUGIN_NOT_ENABLED));
    }

    @Test
    void isEnabledShouldRejectInvalidAuthenticatedUserBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations);
        CurrentUser currentUser = currentUser();
        currentUser.setUserId(null);

        assertThat(service.isEnabled(currentUser)).isFalse();
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void isEnabledShouldRejectBlankUsernameBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");

        assertThat(service.isEnabled(currentUser)).isFalse();
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void isEnabledShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations);
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);

        assertThat(service.isEnabled(currentUser)).isFalse();
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void ensureEnabledShouldRejectWhenSensitiveWordSchemaIsIncomplete() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = true;
        queryOperations.tableExists = true;
        queryOperations.requiredColumnCount = 10L;
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations);

        assertThatThrownBy(() -> service.ensureEnabled(currentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.PLUGIN_NOT_ENABLED));
        assertThat(queryOperations.countQueryCalled).isTrue();
    }

    @Test
    void ensureEnabledShouldRejectWhenAuditUuidColumnsAreMissing() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = true;
        queryOperations.tableExists = true;
        queryOperations.requiredColumnCount = 12L;
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations);

        assertThatThrownBy(() -> service.ensureEnabled(currentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.PLUGIN_NOT_ENABLED));
        assertThat(queryOperations.columnCountSql)
                .contains("created_by_uuid")
                .contains("updated_by_uuid");
    }

    @Test
    void isEnabledShouldRejectDisabledTrustedUserBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations, permissionSnapshotService);
        CurrentUser currentUser = currentUser();
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(false);

        assertThat(service.isEnabled(currentUser)).isFalse();
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.countQueryCalled).isFalse();
        verify(permissionSnapshotService).isTrustedActiveUser(2001L, "user-uuid-2001");
    }

    @Test
    void isEnabledShouldRejectBlankUserUuidBeforeSnapshotAndDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations, permissionSnapshotService);
        CurrentUser currentUser = currentUser();
        currentUser.setUserUuid(" ");

        assertThat(service.isEnabled(currentUser)).isFalse();
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void isEnabledShouldRejectDisabledTrustedIdentityBeforeSnapshotAndDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations, permissionSnapshotService, systemInternalApi);
        CurrentUser currentUser = currentUser();
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "admin-live", "DISABLED"));

        assertThat(service.isEnabled(currentUser)).isFalse();
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.countQueryCalled).isFalse();
        verify(permissionSnapshotService, never()).isTrustedActiveUser(2001L, "user-uuid-2001");
    }

    @Test
    void isEnabledShouldRejectBlankLiveUsernameBeforeSnapshotAndDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations, permissionSnapshotService, systemInternalApi);
        CurrentUser currentUser = currentUser();
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", " ", "ENABLED"));

        assertThat(service.isEnabled(currentUser)).isFalse();
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.countQueryCalled).isFalse();
        verify(permissionSnapshotService, never()).isTrustedActiveUser(2001L, "user-uuid-2001");
    }

    @Test
    void isEnabledShouldRefreshLiveUsernameFromTrustedIdentity() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = true;
        queryOperations.tableExists = true;
        queryOperations.requiredColumnCount = 14L;
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations, permissionSnapshotService, systemInternalApi);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername("admin-stale");
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "  admin-live  ", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);

        assertThat(service.isEnabled(currentUser)).isTrue();
        assertThat(currentUser.getUsername()).isEqualTo("admin-live");
    }

    @Test
    void isEnabledShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = true;
        queryOperations.tableExists = true;
        queryOperations.requiredColumnCount = 14L;
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations, null, null);

        assertThatThrownBy(() -> service.isEnabled(currentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
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

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setUsername("admin");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean pluginEnabled;
        private boolean tableExists;
        private boolean countQueryCalled;
        private int existsCallCount;
        private Long requiredColumnCount;
        private String columnCountSql = "";

        @Override
        public boolean exists(String sql, Object... args) {
            existsCallCount += 1;
            if (sql.contains("from sys_plugin_definition")) {
                assertThat(sql).contains("sys_plugin_version");
                assertThat(sql).contains("v.is_active = 1");
                assertThat(args).containsExactly("sensitive-words");
                return pluginEnabled;
            }
            if (sql.contains("information_schema.tables")) {
                return tableExists;
            }
            return false;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
                columnCountSql = sql;
                return requiredType.cast(requiredColumnCount);
            }
            return null;
        }
    }
}
