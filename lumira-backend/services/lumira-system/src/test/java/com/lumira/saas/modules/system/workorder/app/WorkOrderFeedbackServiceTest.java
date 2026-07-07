package com.lumira.saas.modules.system.workorder.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.workorder.dto.WorkOrderFeedbackDTO;
import com.lumira.saas.modules.system.workorder.vo.WorkOrderFeedbackVO;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkOrderFeedbackServiceTest {

    @Test
    void workOrderWritesShouldPersistAuditUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/workorder/app/WorkOrderFeedbackService.java"));

        assertThat(source).contains("created_by, created_by_uuid, created_at, updated_by, updated_by_uuid");
        assertThat(source).contains("updated_by_uuid = ?");
        assertThat(source).contains("trustedUserUuid(currentUser)");
        assertThat(source).contains("and status = ?");
        assertThat(source).contains("and submitter_id = ?");
        assertThat(source).contains("and submitter_uuid = ?");
    }

    @Test
    void listShouldRejectUnauthenticatedUserBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));

        assertThatThrownBy(() -> service.list(unauthenticatedUser(), null, null, null, "mine", 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listShouldRejectBlankUsernameBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));
        CurrentUser currentUser = user(Set.of("*", "plugin:work-order-feedback:manage"));
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.list(currentUser, null, null, null, "mine", 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));
        CurrentUser currentUser = user(Set.of("*", "plugin:work-order-feedback:manage"));
        currentUser.setSessionVersion(null);

        assertThatThrownBy(() -> service.list(currentUser, null, null, null, "mine", 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createShouldRejectMissingUserUuidBeforePluginCheckAndDatabaseWrite() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));
        CurrentUser currentUser = user(Set.of("*", "plugin:work-order-feedback:create"));
        currentUser.setUserUuid(" ");
        WorkOrderFeedbackDTO.CreateRequest request = new WorkOrderFeedbackDTO.CreateRequest();
        request.setTitle("Problem");
        request.setDetailHtml("<p>Details</p>");

        assertThatThrownBy(() -> service.create(currentUser, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(pluginStateService);
        verify(jdbcTemplate, never()).update(anyString(), any());
    }

    @Test
    void createShouldRejectWhenLiveSnapshotRevokesCreatePermissionBeforePluginCheckAndDatabaseWrite() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("plugin:work-order-feedback:view")));
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, fileInternalApi, permissionSnapshotService);
        WorkOrderFeedbackDTO.CreateRequest request = new WorkOrderFeedbackDTO.CreateRequest();
        request.setTitle("Problem");
        request.setDetailHtml("<p>Details</p>");

        assertThatThrownBy(() -> service.create(user(Set.of("plugin:work-order-feedback:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
        verify(fileInternalApi, never()).uploadImageForUser(any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void createShouldRejectDisabledTrustedUserIdentityBeforePluginCheckAndDatabaseWrite() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "DISABLED"));
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(
                jdbcTemplate,
                pluginStateService,
                fileInternalApi,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        WorkOrderFeedbackDTO.CreateRequest request = new WorkOrderFeedbackDTO.CreateRequest();
        request.setTitle("Problem");
        request.setDetailHtml("<p>Details</p>");

        assertThatThrownBy(() -> service.create(user(Set.of("plugin:work-order-feedback:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
        verify(fileInternalApi, never()).uploadImageForUser(any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void createShouldRejectTrustedUserIdentityWhenLiveUsernameIsUnavailableBeforePluginCheckAndDatabaseWrite() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(
                jdbcTemplate,
                pluginStateService,
                fileInternalApi,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        WorkOrderFeedbackDTO.CreateRequest request = new WorkOrderFeedbackDTO.CreateRequest();
        request.setTitle("Problem");
        request.setDetailHtml("<p>Details</p>");

        assertThatThrownBy(() -> service.create(user(Set.of("plugin:work-order-feedback:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
        verify(fileInternalApi, never()).uploadImageForUser(any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("plugin:work-order-feedback:create")));
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(
                jdbcTemplate,
                pluginStateService,
                fileInternalApi,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = user(Set.of("plugin:work-order-feedback:create"));
        currentUser.setSimulatedRoleId(0L);
        Method method = WorkOrderFeedbackService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void createShouldRejectRevokedSessionTicketBeforePluginCheckAndDatabaseWrite() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "User context is required"));
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(
                jdbcTemplate,
                pluginStateService,
                fileInternalApi,
                mock(PermissionSnapshotService.class),
                sessionAuthenticationService
        );
        WorkOrderFeedbackDTO.CreateRequest request = new WorkOrderFeedbackDTO.CreateRequest();
        request.setTitle("Problem");
        request.setDetailHtml("<p>Details</p>");

        assertThatThrownBy(() -> service.create(user(Set.of("plugin:work-order-feedback:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
        verify(fileInternalApi, never()).uploadImageForUser(any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void createShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(
                jdbcTemplate,
                pluginStateService,
                fileInternalApi,
                null,
                null,
                null
        );
        WorkOrderFeedbackDTO.CreateRequest request = new WorkOrderFeedbackDTO.CreateRequest();
        request.setTitle("Problem");
        request.setDetailHtml("<p>Details</p>");

        assertThatThrownBy(() -> service.create(user(Set.of("plugin:work-order-feedback:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
        verify(fileInternalApi, never()).uploadImageForUser(any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void createShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(
                jdbcTemplate,
                pluginStateService,
                fileInternalApi,
                permissionSnapshotService,
                null,
                null
        );
        WorkOrderFeedbackDTO.CreateRequest request = new WorkOrderFeedbackDTO.CreateRequest();
        request.setTitle("Problem");
        request.setDetailHtml("<p>Details</p>");

        assertThatThrownBy(() -> service.create(user(Set.of("plugin:work-order-feedback:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
        verify(fileInternalApi, never()).uploadImageForUser(any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void uploadImageShouldDelegateRefreshedLiveUsername() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        doNothing().when(pluginStateService).ensureEnabled(any(CurrentUser.class));
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("*", "plugin:work-order-feedback:create")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "ENABLED"));
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(
                jdbcTemplate,
                pluginStateService,
                fileInternalApi,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = user(Set.of("*", "plugin:work-order-feedback:create"));
        currentUser.setUsername("alice-stale");
        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(fileInternalApi.uploadImageForUser(any(), anyString(), anyString(), anyString(), eq(1001L), eq("user-uuid-1001"), eq("alice-live"), eq(null)))
                .thenReturn(null);

        service.uploadImage(currentUser, file);

        assertThat(currentUser.getUsername()).isEqualTo("alice-live");
        verify(fileInternalApi).uploadImageForUser(any(), anyString(), anyString(), anyString(), eq(1001L), eq("user-uuid-1001"), eq("alice-live"), eq(null));
    }

    @Test
    void uploadImageShouldRejectMissingPermissionsVersionBeforePluginCheckAndFileUpload() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, fileInternalApi);
        CurrentUser currentUser = user(Set.of("*", "plugin:work-order-feedback:create"));
        currentUser.setPermissionsVersion(" ");

        assertThatThrownBy(() -> service.uploadImage(currentUser, mock(org.springframework.web.multipart.MultipartFile.class)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
        verify(fileInternalApi, never()).uploadImageForUser(any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void listShouldRejectAdminScopeWithoutManagePermission() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));

        assertThatThrownBy(() -> service.list(user(Set.of("plugin:work-order-feedback:view")), null, null, null, "admin", 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listMineShouldRequireViewPermissionAtServiceLayer() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));

        assertThatThrownBy(() -> service.list(user(Set.of("plugin:work-order-feedback:create")), null, null, null, "mine", 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listMineShouldFilterBySubmitterUuid() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackService service = service(jdbcTemplate);
        when(jdbcTemplate.query(
                contains("submitter_uuid = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                eq(1001L),
                eq("user-uuid-1001"),
                eq(10L),
                eq(0L)
        )).thenReturn(List.of());

        service.list(user(Set.of("plugin:work-order-feedback:view")), null, null, null, "mine", 1, 10);

        verify(jdbcTemplate).query(
                contains("submitter_uuid = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                eq(1001L),
                eq("user-uuid-1001"),
                eq(10L),
                eq(0L)
        );
    }

    @Test
    void detailShouldRejectAdminScopeWithoutManagePermission() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));

        assertThatThrownBy(() -> service.detail(user(Set.of("plugin:work-order-feedback:view")), 100L, "admin"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void detailMineShouldRequireViewPermissionBeforeLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));

        assertThatThrownBy(() -> service.detail(user(Set.of("plugin:work-order-feedback:create")), 100L, "mine"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void detailMineShouldFilterBySubmitterUuid() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.detail(user(Set.of("plugin:work-order-feedback:view")), 100L, "mine"))
                .isInstanceOf(RuntimeException.class);

        verify(jdbcTemplate).queryForObject(
                contains("submitter_uuid = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                eq(100L),
                eq(1001L),
                eq("user-uuid-1001")
        );
    }

    @Test
    void detailShouldRejectInvalidIdBeforePluginCheckAndLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));

        assertThatThrownBy(() -> service.detail(user(Set.of("plugin:work-order-feedback:view")), 0L, "mine"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createShouldRequireCreatePermissionBeforeDatabaseWrite() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));
        WorkOrderFeedbackDTO.CreateRequest request = new WorkOrderFeedbackDTO.CreateRequest();
        request.setTitle("Problem");
        request.setDetailHtml("<p>Details</p>");

        assertThatThrownBy(() -> service.create(user(Set.of("plugin:work-order-feedback:view")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(pluginStateService);
        verify(jdbcTemplate, never()).update(anyString(), any());
    }

    @Test
    void createShouldRejectNullRequestAsBadRequestWithoutRuntimeException() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.create(user(Set.of("plugin:work-order-feedback:create")), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(jdbcTemplate, never()).update(anyString(), any());
    }

    @Test
    void createShouldRejectWhenInsertMissesBeforeReadingGeneratedId() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackService service = service(jdbcTemplate);
        WorkOrderFeedbackDTO.CreateRequest request = new WorkOrderFeedbackDTO.CreateRequest();
        request.setTitle("Problem");
        request.setDetailHtml("<p>Details</p>");
        when(jdbcTemplate.update(contains("insert into sys_work_order_feedback"), any())).thenReturn(0);

        assertThatThrownBy(() -> service.create(user(Set.of("plugin:work-order-feedback:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));

        verify(jdbcTemplate, never()).queryForObject(eq("select last_insert_id()"), eq(Long.class));
    }

    @Test
    void updateStatusShouldRequireManagePermissionAtServiceLayer() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));
        WorkOrderFeedbackDTO.StatusRequest request = new WorkOrderFeedbackDTO.StatusRequest();
        request.setStatus("RESOLVED");

        assertThatThrownBy(() -> service.updateStatus(user(Set.of("plugin:work-order-feedback:view")), 100L, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(pluginStateService);
        verify(jdbcTemplate, never()).update(anyString(), any());
    }

    @Test
    void updateStatusShouldRejectInvalidIdBeforePluginCheckAndWrite() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));
        WorkOrderFeedbackDTO.StatusRequest request = new WorkOrderFeedbackDTO.StatusRequest();
        request.setStatus("RESOLVED");

        assertThatThrownBy(() -> service.updateStatus(user(Set.of("plugin:work-order-feedback:manage")), -1L, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(pluginStateService);
        verify(jdbcTemplate, never()).update(anyString(), any());
    }

    @Test
    void updateStatusShouldBindFinalWriteToLoadedStatusAndSubmitterUuid() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        WorkOrderFeedbackService service = service(jdbcTemplate);
        WorkOrderFeedbackDTO.StatusRequest request = new WorkOrderFeedbackDTO.StatusRequest();
        request.setStatus("RESOLVED");
        request.setAdminReply("done");

        service.updateStatus(user(Set.of("plugin:work-order-feedback:manage")), 100L, request);

        assertThat(jdbcTemplate.lastUpdateSql)
                .contains("and status = ?")
                .contains("and submitter_id = ?")
                .contains("and submitter_uuid = ?");
        assertThat(jdbcTemplate.lastUpdateArgs)
                .containsSequence(100L, "OPEN", 2002L, "user-uuid-2002");
    }

    @Test
    void uploadImageShouldRejectBlankUsernameBeforeFileUpload() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        doNothing().when(pluginStateService).ensureEnabled(any(CurrentUser.class));
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, fileInternalApi);
        CurrentUser currentUser = user(Set.of("*", "plugin:work-order-feedback:create"));
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.uploadImage(currentUser, mock(org.springframework.web.multipart.MultipartFile.class)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
        verify(fileInternalApi, never()).uploadImageForUser(any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void uploadImageShouldRequireCreatePermissionBeforeFileUpload() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        doNothing().when(pluginStateService).ensureEnabled(any(CurrentUser.class));
        WorkOrderFeedbackService service = new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, fileInternalApi);

        assertThatThrownBy(() -> service.uploadImage(user(Set.of("plugin:work-order-feedback:view")), mock(org.springframework.web.multipart.MultipartFile.class)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(pluginStateService);
        verifyNoInteractions(jdbcTemplate);
        verify(fileInternalApi, never()).uploadImageForUser(any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    private WorkOrderFeedbackService service(MyBatisQueryOperations jdbcTemplate) {
        WorkOrderFeedbackPluginStateService pluginStateService = mock(WorkOrderFeedbackPluginStateService.class);
        doNothing().when(pluginStateService).ensureEnabled(any(CurrentUser.class));
        return new WorkOrderFeedbackService(jdbcTemplate, pluginStateService, mock(FileInternalApi.class));
    }

    private CurrentUser user(Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("alice");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setPermissions(permissions);
        return currentUser;
    }

    private CurrentUser unauthenticatedUser() {
        CurrentUser currentUser = user(Set.of("*", "plugin:work-order-feedback:manage"));
        currentUser.setAuthenticated(false);
        return currentUser;
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
        private String lastUpdateSql;
        private List<Object> lastUpdateArgs = List.of();

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            this.lastUpdateArgs = java.util.Arrays.asList(args);
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            WorkOrderFeedbackVO.WorkOrderRecord record = new WorkOrderFeedbackVO.WorkOrderRecord();
            record.setId(100L);
            record.setStatus("OPEN");
            record.setSubmitterId(2002L);
            record.setSubmitterUuid("user-uuid-2002");
            return (T) record;
        }
    }
}
