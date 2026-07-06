package com.lumira.saas.modules.system.user.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.export.ExcelExportService;
import com.lumira.saas.modules.system.export.ExportDTO;
import com.lumira.saas.modules.system.export.ExportTaskEntity;
import com.lumira.saas.modules.system.export.ExportTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserExportAppServiceTest {

    @Test
    void listUserExportFieldsShouldRequireExportPermission() {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        UserExportAppService service = new UserExportAppService(
                mock(SystemUserManagementAppService.class),
                mock(ExcelExportService.class),
                mock(ExportTaskService.class),
                permissionSnapshotService,
                executorServiceProvider(mock(ExecutorService.class))
        );
        CurrentUser currentUser = trustedUser(Set.of("system:user:view"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:view")));

        BizException exception = assertThrows(BizException.class, () -> service.listUserExportFields(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void listUserExportFieldsShouldRejectUntrustedUser() {
        UserExportAppService service = new UserExportAppService(
                mock(SystemUserManagementAppService.class),
                mock(ExcelExportService.class),
                mock(ExportTaskService.class),
                mock(PermissionSnapshotService.class),
                executorServiceProvider(mock(ExecutorService.class))
        );
        CurrentUser currentUser = trustedUser(Set.of("system:user:export"));
        currentUser.setUsername(" ");

        BizException exception = assertThrows(BizException.class, () -> service.listUserExportFields(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void listUserExportFieldsShouldRejectWhenLiveSnapshotRevokesExportPermission() {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        UserExportAppService service = new UserExportAppService(
                mock(SystemUserManagementAppService.class),
                mock(ExcelExportService.class),
                mock(ExportTaskService.class),
                permissionSnapshotService,
                executorServiceProvider(mock(ExecutorService.class))
        );
        CurrentUser currentUser = trustedUser(Set.of("system:user:export"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:view")));

        BizException exception = assertThrows(BizException.class, () -> service.listUserExportFields(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void listUserExportFieldsShouldRejectMissingSessionVersion() {
        UserExportAppService service = new UserExportAppService(
                mock(SystemUserManagementAppService.class),
                mock(ExcelExportService.class),
                mock(ExportTaskService.class),
                mock(PermissionSnapshotService.class),
                executorServiceProvider(mock(ExecutorService.class))
        );
        CurrentUser currentUser = trustedUser(Set.of("system:user:export"));
        currentUser.setSessionVersion(null);

        BizException exception = assertThrows(BizException.class, () -> service.listUserExportFields(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void exportUsersShouldRejectNullRequestBeforeUserLookup() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        UserExportAppService service = new UserExportAppService(
                userManagementAppService,
                mock(ExcelExportService.class),
                mock(ExportTaskService.class),
                permissionSnapshotService,
                executorServiceProvider(mock(ExecutorService.class))
        );
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:export")));

        BizException exception = assertThrows(BizException.class, () -> service.exportUsers(trustedUser(Set.of("system:user:export")), null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        verify(userManagementAppService, never()).listUsers(
                org.mockito.Mockito.any(CurrentUser.class),
                org.mockito.Mockito.any(Long.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(Long.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(Long.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.anyLong(),
                org.mockito.Mockito.anyLong()
        );
    }

    @Test
    void exportUsersShouldRequireSensitivePermissionBeforeUserLookup() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        UserExportAppService service = service(userManagementAppService, permissionSnapshotService);
        ExportDTO.UserExportRequest request = request(List.of("id", "idCardNumber"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:export")));

        BizException exception = assertThrows(BizException.class, () -> service.exportUsers(trustedUser(Set.of("system:user:export")), request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoUserLookup(userManagementAppService);
    }

    @Test
    void exportUsersShouldRequireTrustedSessionForSensitiveFields() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        UserExportAppService service = service(userManagementAppService);
        ExportDTO.UserExportRequest request = request(List.of("id", "idCardNumber"));
        CurrentUser currentUser = trustedUser(Set.of("system:user:export", "system:user:sensitive:view"));
        currentUser.setSessionId(null);

        BizException exception = assertThrows(BizException.class, () -> service.exportUsers(currentUser, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoUserLookup(userManagementAppService);
    }

    @Test
    void exportUsersShouldRejectBlankFieldBeforeUserLookup() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        UserExportAppService service = service(userManagementAppService, permissionSnapshotService);
        ExportDTO.UserExportRequest request = request(List.of("id", " "));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:export")));

        BizException exception = assertThrows(BizException.class, () -> service.exportUsers(trustedUser(Set.of("system:user:export")), request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        verifyNoUserLookup(userManagementAppService);
    }

    @Test
    void exportUsersShouldRejectTooManyFieldsBeforeUserLookup() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        UserExportAppService service = service(userManagementAppService, permissionSnapshotService);
        List<String> fields = new ArrayList<>();
        for (int i = 0; i < 31; i += 1) {
            fields.add("id");
        }
        ExportDTO.UserExportRequest request = request(fields);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:export")));

        BizException exception = assertThrows(BizException.class, () -> service.exportUsers(trustedUser(Set.of("system:user:export")), request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        verifyNoUserLookup(userManagementAppService);
    }

    @Test
    void exportUsersShouldRejectInvalidFiltersBeforeUserLookup() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        UserExportAppService service = service(userManagementAppService, permissionSnapshotService);
        ExportDTO.UserExportRequest request = request(List.of("id"));
        request.setDeptId(0L);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:export")));

        BizException exception = assertThrows(BizException.class, () -> service.exportUsers(trustedUser(Set.of("system:user:export")), request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        verifyNoUserLookup(userManagementAppService);
    }

    @Test
    void exportUsersShouldCreateAsyncTaskWithNormalizedRequestSnapshot() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        ExportTaskService exportTaskService = mock(ExportTaskService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        UserExportAppService service = new UserExportAppService(
                userManagementAppService,
                mock(ExcelExportService.class),
                exportTaskService,
                permissionSnapshotService,
                executorServiceProvider(mock(ExecutorService.class))
        );
        PageResponse<com.lumira.saas.modules.system.vo.SystemVO.UserVO> countPage = new PageResponse<>();
        countPage.setTotal(5001L);
        when(userManagementAppService.listUsers(
                any(CurrentUser.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyLong()
        )).thenReturn(countPage);
        ExportTaskEntity task = new ExportTaskEntity();
        task.setId(9001L);
        when(exportTaskService.createTask(any(CurrentUser.class), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), eq(5001L)))
                .thenReturn(task);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:export")));
        ExportDTO.UserExportRequest request = request(new ArrayList<>(List.of(" id ", "username")));
        request.setUsername(" alice ");

        service.exportUsers(trustedUser(Set.of("system:user:export")), request);
        request.getFields().add("idCardNumber");
        request.setUsername("mallory");

        ArgumentCaptor<ExportDTO.UserExportRequest> requestCaptor = ArgumentCaptor.forClass(ExportDTO.UserExportRequest.class);
        verify(exportTaskService).createTask(any(CurrentUser.class), eq("system:user"), requestCaptor.capture(), anyList(), eq(5001L));
        assertThat(requestCaptor.getValue()).isNotSameAs(request);
        assertThat(requestCaptor.getValue().getFields()).containsExactly("id", "username");
        assertThat(requestCaptor.getValue().getUsername()).isEqualTo("alice");

        service.shutdown();
    }

    @Test
    void exportUsersShouldRunAsyncTaskWithTrustedUserSnapshot() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExportTaskService exportTaskService = mock(ExportTaskService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        RecordingExecutorService executorService = new RecordingExecutorService();
        UserExportAppService service = new UserExportAppService(
                userManagementAppService,
                excelExportService,
                exportTaskService,
                permissionSnapshotService,
                executorServiceProvider(executorService)
        );
        PageResponse<com.lumira.saas.modules.system.vo.SystemVO.UserVO> page = new PageResponse<>();
        page.setTotal(5001L);
        page.setRecords(List.of());
        page.setHasMore(false);
        when(userManagementAppService.listUsers(
                any(CurrentUser.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyLong()
        )).thenReturn(page);
        when(excelExportService.export(anyString(), anyList(), anyList())).thenReturn(new byte[]{1});
        ExportTaskEntity task = new ExportTaskEntity();
        task.setId(9001L);
        when(exportTaskService.createTask(any(CurrentUser.class), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), eq(5001L)))
                .thenReturn(task);
        CurrentUser currentUser = trustedUser(Set.of("system:user:export"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:export")));

        service.exportUsers(currentUser, request(List.of("id")));
        currentUser.setUserUuid(" ");
        currentUser.setSessionVersion(null);
        currentUser.setPermissions(Set.of());
        executorService.runSubmitted();

        ArgumentCaptor<CurrentUser> runningUserCaptor = ArgumentCaptor.forClass(CurrentUser.class);
        verify(exportTaskService).markRunning(runningUserCaptor.capture(), eq(9001L));
        CurrentUser asyncUser = runningUserCaptor.getValue();
        assertThat(asyncUser).isNotSameAs(currentUser);
        assertThat(asyncUser.getUserId()).isEqualTo(1001L);
        assertThat(asyncUser.getUserUuid()).isEqualTo("user-uuid-1001");
        assertThat(asyncUser.getSessionId()).isEqualTo("session-1");
        assertThat(asyncUser.getSessionVersion()).isEqualTo(1);
        assertThat(asyncUser.getPermissionsVersion()).isEqualTo("permissions-2");
        assertThat(asyncUser.getPermissions()).containsExactly("system:user:export");
    }

    @Test
    void exportUsersShouldRejectWhenUserIsNoLongerTrustedBeforeTaskCreation() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExportTaskService exportTaskService = mock(ExportTaskService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        RecordingExecutorService executorService = new RecordingExecutorService();
        UserExportAppService service = new UserExportAppService(
                userManagementAppService,
                excelExportService,
                exportTaskService,
                permissionSnapshotService,
                executorServiceProvider(executorService)
        );
        PageResponse<com.lumira.saas.modules.system.vo.SystemVO.UserVO> page = new PageResponse<>();
        page.setTotal(5001L);
        when(userManagementAppService.listUsers(
                any(CurrentUser.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyLong()
        )).thenReturn(page);
        ExportTaskEntity task = new ExportTaskEntity();
        task.setId(9001L);
        when(exportTaskService.createTask(any(CurrentUser.class), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), eq(5001L)))
                .thenReturn(task);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(false);

        BizException exception = assertThrows(BizException.class, () -> service.exportUsers(trustedUser(Set.of("system:user:export")), request(List.of("id"))));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(exportTaskService, never()).createTask(any(CurrentUser.class), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), eq(5001L));
        verify(exportTaskService, never()).markRunning(any(CurrentUser.class), eq(9001L));
        verify(excelExportService, never()).export(anyString(), anyList(), anyList());
    }

    @Test
    void exportUsersShouldRejectDisabledTrustedIdentityBeforeTaskCreation() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        ExportTaskService exportTaskService = mock(ExportTaskService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        RecordingExecutorService executorService = new RecordingExecutorService();
        UserExportAppService service = new UserExportAppService(
                userManagementAppService,
                mock(ExcelExportService.class),
                exportTaskService,
                permissionSnapshotService,
                systemInternalApi,
                null,
                executorServiceProvider(executorService)
        );
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "operator-live", "DISABLED"));

        BizException exception = assertThrows(BizException.class, () -> service.exportUsers(trustedUser(Set.of("system:user:export")), request(List.of("id"))));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(permissionSnapshotService, never()).loadSnapshot(1001L, "user-uuid-1001");
        verify(exportTaskService, never()).createTask(any(CurrentUser.class), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), anyLong());
        verifyNoUserLookup(userManagementAppService);
    }

    @Test
    void exportUsersShouldRefreshLiveUsernameBeforeTaskCreation() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        ExportTaskService exportTaskService = mock(ExportTaskService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        RecordingExecutorService executorService = new RecordingExecutorService();
        UserExportAppService service = new UserExportAppService(
                userManagementAppService,
                mock(ExcelExportService.class),
                exportTaskService,
                permissionSnapshotService,
                systemInternalApi,
                null,
                executorServiceProvider(executorService)
        );
        PageResponse<com.lumira.saas.modules.system.vo.SystemVO.UserVO> countPage = new PageResponse<>();
        countPage.setTotal(5001L);
        when(userManagementAppService.listUsers(
                any(CurrentUser.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyLong()
        )).thenReturn(countPage);
        ExportTaskEntity task = new ExportTaskEntity();
        task.setId(9001L);
        when(exportTaskService.createTask(any(CurrentUser.class), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), eq(5001L)))
                .thenReturn(task);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "operator-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:export")));
        CurrentUser currentUser = trustedUser(Set.of("system:user:export"));
        currentUser.setUsername("operator-stale");

        service.exportUsers(currentUser, request(List.of("id")));

        ArgumentCaptor<CurrentUser> currentUserCaptor = ArgumentCaptor.forClass(CurrentUser.class);
        verify(exportTaskService).createTask(currentUserCaptor.capture(), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), eq(5001L));
        assertThat(currentUserCaptor.getValue().getUsername()).isEqualTo("operator-live");
        assertThat(currentUserCaptor.getValue().getPermissionsVersion()).isEqualTo("permissions-2");
        assertThat(currentUser.getUsername()).isEqualTo("operator-stale");
    }

    @Test
    void exportUsersShouldRejectWhenExportPermissionWasRevokedBeforeTaskCreation() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExportTaskService exportTaskService = mock(ExportTaskService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        RecordingExecutorService executorService = new RecordingExecutorService();
        UserExportAppService service = new UserExportAppService(
                userManagementAppService,
                excelExportService,
                exportTaskService,
                permissionSnapshotService,
                executorServiceProvider(executorService)
        );
        PageResponse<com.lumira.saas.modules.system.vo.SystemVO.UserVO> page = new PageResponse<>();
        page.setTotal(5001L);
        when(userManagementAppService.listUsers(
                any(CurrentUser.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyLong()
        )).thenReturn(page);
        ExportTaskEntity task = new ExportTaskEntity();
        task.setId(9001L);
        when(exportTaskService.createTask(any(CurrentUser.class), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), eq(5001L)))
                .thenReturn(task);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:view")));

        BizException exception = assertThrows(BizException.class, () -> service.exportUsers(trustedUser(Set.of("system:user:export")), request(List.of("id"))));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(exportTaskService, never()).createTask(any(CurrentUser.class), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), eq(5001L));
        verify(exportTaskService, never()).markRunning(any(CurrentUser.class), eq(9001L));
        verify(excelExportService, never()).export(anyString(), anyList(), anyList());
    }

    @Test
    void exportUsersShouldRejectWhenLiveSnapshotRevokesExportPermissionBeforeUserLookup() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        UserExportAppService service = new UserExportAppService(
                userManagementAppService,
                mock(ExcelExportService.class),
                mock(ExportTaskService.class),
                permissionSnapshotService,
                executorServiceProvider(mock(ExecutorService.class))
        );
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Set.of("system:user:view")));

        BizException exception = assertThrows(BizException.class, () -> service.exportUsers(trustedUser(Set.of("system:user:export")), request(List.of("id"))));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoUserLookup(userManagementAppService);
    }

    @Test
    void exportUsersShouldRefreshAsyncTaskAgainstSimulatedRoleSnapshot() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExportTaskService exportTaskService = mock(ExportTaskService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        RecordingExecutorService executorService = new RecordingExecutorService();
        UserExportAppService service = new UserExportAppService(
                userManagementAppService,
                excelExportService,
                exportTaskService,
                permissionSnapshotService,
                executorServiceProvider(executorService)
        );
        PageResponse<com.lumira.saas.modules.system.vo.SystemVO.UserVO> page = new PageResponse<>();
        page.setTotal(5001L);
        page.setRecords(List.of());
        page.setHasMore(false);
        when(userManagementAppService.listUsers(
                any(CurrentUser.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyLong()
        )).thenReturn(page);
        when(excelExportService.export(anyString(), anyList(), anyList())).thenReturn(new byte[]{1});
        ExportTaskEntity task = new ExportTaskEntity();
        task.setId(9001L);
        when(exportTaskService.createTask(any(CurrentUser.class), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), eq(5001L)))
                .thenReturn(task);
        CurrentUser currentUser = trustedUser(Set.of("system:user:export"));
        currentUser.setSimulatedRoleId(77L);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadRoleSnapshot(77L)).thenReturn(permissionSnapshot(Set.of("system:user:export")));

        service.exportUsers(currentUser, request(List.of("id")));
        executorService.runSubmitted();

        verify(permissionSnapshotService, org.mockito.Mockito.atLeast(2)).loadRoleSnapshot(77L);
        verify(permissionSnapshotService, never()).loadSnapshot(1001L, "user-uuid-1001");
        verify(exportTaskService).markRunning(any(CurrentUser.class), eq(9001L));
    }

    @Test
    void exportUsersShouldStopAsyncUploadWhenSessionTicketIsRevokedMidFlight() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExportTaskService exportTaskService = mock(ExportTaskService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        RecordingExecutorService executorService = new RecordingExecutorService();
        UserExportAppService service = new UserExportAppService(
                userManagementAppService,
                excelExportService,
                exportTaskService,
                permissionSnapshotService,
                sessionAuthenticationService,
                executorServiceProvider(executorService)
        );
        PageResponse<com.lumira.saas.modules.system.vo.SystemVO.UserVO> countPage = new PageResponse<>();
        countPage.setTotal(5001L);
        PageResponse<com.lumira.saas.modules.system.vo.SystemVO.UserVO> dataPage = new PageResponse<>();
        dataPage.setRecords(List.of());
        dataPage.setHasMore(false);
        when(userManagementAppService.listUsers(
                any(CurrentUser.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyLong()
        )).thenReturn(countPage, dataPage);
        when(excelExportService.export(anyString(), anyList(), anyList())).thenReturn(new byte[]{1});
        ExportTaskEntity task = new ExportTaskEntity();
        task.setId(9001L);
        when(exportTaskService.createTask(any(CurrentUser.class), eq("system:user"), any(ExportDTO.UserExportRequest.class), anyList(), eq(5001L)))
                .thenReturn(task);
        BizException revoked = new BizException(ErrorCode.UNAUTHORIZED, "Session expired");
        when(sessionAuthenticationService.authenticateSessionTicket(
                eq("session-1"),
                eq(1001L),
                eq("user-uuid-1001"),
                eq(null),
                eq(1),
                anyString()
        )).thenReturn(
                authenticatedAccess(Set.of("system:user:export"), "permissions-2"),
                authenticatedAccess(Set.of("system:user:export"), "permissions-2"),
                authenticatedAccess(Set.of("system:user:export"), "permissions-2"),
                authenticatedAccess(Set.of("system:user:export"), "permissions-2")
        ).thenThrow(revoked, revoked);

        service.exportUsers(trustedUser(Set.of("system:user:export")), request(List.of("id")));
        executorService.runSubmitted();

        verify(exportTaskService).markRunning(any(CurrentUser.class), eq(9001L));
        verify(exportTaskService, never()).uploadExportFile(any(CurrentUser.class), any(byte[].class), anyString(), anyString(), anyString(), anyString());
        verify(exportTaskService, never()).markSuccess(any(CurrentUser.class), eq(9001L), any(), anyString());
        verify(exportTaskService, never()).markFailed(any(CurrentUser.class), eq(9001L), any(Exception.class));
    }

    private UserExportAppService service(SystemUserManagementAppService userManagementAppService) {
        return service(userManagementAppService, mock(PermissionSnapshotService.class));
    }

    private UserExportAppService service(SystemUserManagementAppService userManagementAppService, PermissionSnapshotService permissionSnapshotService) {
        return new UserExportAppService(
                userManagementAppService,
                mock(ExcelExportService.class),
                mock(ExportTaskService.class),
                permissionSnapshotService,
                null,
                null,
                executorServiceProvider(mock(ExecutorService.class))
        );
    }

    private ExportDTO.UserExportRequest request(List<String> fields) {
        ExportDTO.UserExportRequest request = new ExportDTO.UserExportRequest();
        request.setFields(fields);
        return request;
    }

    private void verifyNoUserLookup(SystemUserManagementAppService userManagementAppService) {
        verify(userManagementAppService, never()).listUsers(
                org.mockito.Mockito.any(CurrentUser.class),
                org.mockito.Mockito.any(Long.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(Long.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.any(Long.class),
                org.mockito.Mockito.any(String.class),
                org.mockito.Mockito.anyLong(),
                org.mockito.Mockito.anyLong()
        );
    }

    private CurrentUser trustedUser(Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("operator");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(permissions);
        return currentUser;
    }

    private SessionAuthenticationService.AuthenticatedAccess authenticatedAccess(Set<String> permissions, String permissionsVersion) {
        CurrentUser currentUser = trustedUser(permissions);
        currentUser.setPermissionsVersion(permissionsVersion);
        return new SessionAuthenticationService.AuthenticatedAccess(currentUser, null, false);
    }

    private PermissionSnapshotService.PermissionSnapshot permissionSnapshot(Set<String> permissions) {
        return new PermissionSnapshotService.PermissionSnapshot(
                "permissions-2",
                permissions,
                Set.of(11L),
                21L,
                Set.of(21L),
                Set.of(22L),
                List.of(),
                "/dashboard/home"
        );
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

    private static ObjectProvider<ExecutorService> executorServiceProvider(ExecutorService executorService) {
        return new ObjectProvider<>() {
            @Override
            public ExecutorService getObject(Object... args) {
                return executorService;
            }

            @Override
            public ExecutorService getIfAvailable() {
                return executorService;
            }

            @Override
            public ExecutorService getIfUnique() {
                return executorService;
            }

            @Override
            public ExecutorService getObject() {
                return executorService;
            }

            @Override
            public Iterator<ExecutorService> iterator() {
                return executorService == null ? List.<ExecutorService>of().iterator() : List.of(executorService).iterator();
            }

            @Override
            public Stream<ExecutorService> stream() {
                return executorService == null ? Stream.empty() : Stream.of(executorService);
            }

            @Override
            public Stream<ExecutorService> orderedStream() {
                return stream();
            }
        };
    }

    private static final class RecordingExecutorService extends AbstractExecutorService {
        private Runnable submitted;
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return submitted == null ? List.of() : List.of(submitted);
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            submitted = command;
        }

        private void runSubmitted() {
            submitted.run();
        }
    }
}
