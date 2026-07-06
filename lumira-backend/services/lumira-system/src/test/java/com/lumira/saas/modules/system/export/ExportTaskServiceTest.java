package com.lumira.saas.modules.system.export;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportTaskServiceTest {

    @Test
    void markRunningShouldRejectWhenSessionTicketIsRevokedBeforeUpdate() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket(
                "session-1",
                1001L,
                "user-uuid-1001",
                null,
                1,
                "permissions-1"
        )).thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        ExportTaskService service = new ExportTaskService(
                exportTaskMapper,
                fileInternalApi,
                new ObjectMapper(),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> service.markRunning(currentUser(), 9001L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(exportTaskMapper, never()).update(any(ExportTaskEntity.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void uploadExportFileShouldStoreAsPersonalFile() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());
        CurrentUser currentUser = currentUser();

        when(fileInternalApi.uploadDocumentForUser(any(), eq("user-export"), eq("export,user"), eq("report"), eq(null), eq(1001L), eq("user-uuid-1001"), eq("alice")))
                .thenReturn(fileObject(501L));

        FileObjectDTO uploaded = service.uploadExportFile(currentUser, new byte[]{1, 2, 3}, "users.xlsx", "user-export", "export,user", "report");

        assertThat(uploaded.id()).isEqualTo(501L);
        verify(fileInternalApi).uploadDocumentForUser(
                any(),
                eq("user-export"),
                eq("export,user"),
                eq("report"),
                eq(null),
                eq(1001L),
                eq("user-uuid-1001"),
                eq("alice")
        );
    }

    @Test
    void getTaskShouldReturnPersonalDownloadUrl() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());
        ExportTaskEntity task = new ExportTaskEntity();
        task.setId(9001L);
        task.setModuleKey("system:user");
        task.setStatus(ExportTaskService.STATUS_SUCCESS);
        task.setTotalCount(12L);
        task.setFileId(501L);
        task.setFileName("users.xlsx");
        task.setCreatedBy(1001L);
        task.setCreatedAt(LocalDateTime.now());
        task.setDeleted(0);
        when(exportTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);

        ExportVO.ExportTaskVO vo = service.getTask(currentUser(), 9001L);

        assertThat(vo.getDownloadUrl()).isEqualTo("/api/v1/files/501/download");
        assertThat(vo.getDownloadUrl()).doesNotContain("download-center");
        verify(exportTaskMapper).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void markSuccessShouldScopeUpdateToTaskOwner() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());
        ArgumentCaptor<LambdaUpdateWrapper<ExportTaskEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        when(exportTaskMapper.update(any(ExportTaskEntity.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.markSuccess(currentUser(), 9001L, fileObject(501L), "users.xlsx");

        verify(exportTaskMapper).update(any(ExportTaskEntity.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getExpression().getNormal()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void createTaskShouldPersistTrustedCreatorUuid() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:user:export")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "ENABLED"));
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper(), permissionSnapshotService, systemInternalApi, null);
        ArgumentCaptor<ExportTaskEntity> entityCaptor = ArgumentCaptor.forClass(ExportTaskEntity.class);
        when(exportTaskMapper.insert(any(ExportTaskEntity.class))).thenReturn(1);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername("alice-stale");

        service.createTask(currentUser, "system:user", java.util.Map.of("keyword", "alice"), java.util.List.of("username"), 1L);

        verify(exportTaskMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getCreatedBy()).isEqualTo(1001L);
        assertThat(entityCaptor.getValue().getCreatedByUuid()).isEqualTo("user-uuid-1001");
        assertThat(currentUser.getUsername()).isEqualTo("alice-live");
    }

    @Test
    void createTaskShouldRejectDisabledTrustedUserIdentityBeforeInsert() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "DISABLED"));
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper(), permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.createTask(currentUser(), "system:user", new Object(), Set.of("username").stream().toList(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(exportTaskMapper, never()).insert(any(ExportTaskEntity.class));
    }

    @Test
    void createTaskShouldRejectWhenInsertMisses() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());
        when(exportTaskMapper.insert(any(ExportTaskEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> service.createTask(currentUser(), "system:user", java.util.Map.of("keyword", "alice"), java.util.List.of("username"), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR))
                .hasMessageContaining("Export task changed");
    }

    @Test
    void markSuccessShouldRejectUntrustedUploadedFileBeforeUpdate() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());

        assertThatThrownBy(() -> service.markSuccess(currentUser(), 9001L, fileObject(0L), "users.xlsx"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.markSuccess(currentUser(), 9001L, fileObject(501L, 2002L), "users.xlsx"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.markSuccess(currentUser(), 9001L, fileObject(501L, 1001L, "user-uuid-2002"), "users.xlsx"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.markSuccess(currentUser(), 9001L, fileObject(501L, null), "users.xlsx"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(exportTaskMapper, never()).update(any(ExportTaskEntity.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void statusUpdatesShouldIncludeCreatedByOwnerCondition() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/export/ExportTaskService.java"));

        assertThat(source)
                .contains(".eq(ExportTaskEntity::getCreatedBy, currentUserId(currentUser))")
                .contains(".eq(ExportTaskEntity::getCreatedByUuid, trustedUserUuid(currentUser))")
                .contains(".eq(ExportTaskEntity::getStatus, STATUS_PENDING)")
                .contains(".eq(ExportTaskEntity::getStatus, STATUS_RUNNING)")
                .contains(".in(ExportTaskEntity::getStatus, STATUS_PENDING, STATUS_RUNNING)")
                .contains("Export task changed, please retry");
    }

    @Test
    void getTaskShouldIncludeCreatorUuidOwnerCondition() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/export/ExportTaskService.java"));

        assertThat(source)
                .contains(".eq(ExportTaskEntity::getCreatedBy, userId)")
                .contains(".eq(ExportTaskEntity::getCreatedByUuid, trustedUserUuid(currentUser))");
    }

    @Test
    void createTaskShouldRejectAnonymousUser() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());

        assertThatThrownBy(() -> service.createTask(null, "system:user", new Object(), Set.of("username").stream().toList(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(exportTaskMapper, never()).insert(any(ExportTaskEntity.class));
    }

    @Test
    void createTaskShouldRejectUnauthenticatedUserBeforeInsert() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());
        CurrentUser currentUser = currentUser();
        currentUser.setAuthenticated(false);
        currentUser.setPermissions(Set.of("*", "system:user:export"));

        assertThatThrownBy(() -> service.createTask(currentUser, "system:user", new Object(), Set.of("username").stream().toList(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(exportTaskMapper, never()).insert(any(ExportTaskEntity.class));
    }

    @Test
    void createTaskShouldRejectBlankUsernameBeforeInsert() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.createTask(currentUser, "system:user", new Object(), Set.of("username").stream().toList(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(exportTaskMapper, never()).insert(any(ExportTaskEntity.class));
    }

    @Test
    void createTaskShouldRequireExportPermissionBeforeInsert() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());
        CurrentUser currentUser = currentUser();
        currentUser.setPermissions(Set.of("system:user:view"));

        assertThatThrownBy(() -> service.createTask(currentUser, "system:user", new Object(), Set.of("username").stream().toList(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(exportTaskMapper, never()).insert(any(ExportTaskEntity.class));
    }

    @Test
    void createTaskShouldRejectWhenLiveSnapshotRevokesExportPermissionBeforeInsert() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:user:view")));
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper(), permissionSnapshotService);

        assertThatThrownBy(() -> service.createTask(currentUser(), "system:user", new Object(), Set.of("username").stream().toList(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(exportTaskMapper, never()).insert(any(ExportTaskEntity.class));
    }

    @Test
    void createTaskShouldRejectInvalidParametersBeforeInsert() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());

        assertThatThrownBy(() -> service.createTask(currentUser(), " ", new Object(), Set.of("username").stream().toList(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.createTask(currentUser(), "system:user", null, Set.of("username").stream().toList(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.createTask(currentUser(), "system:user", new Object(), java.util.List.of(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.createTask(currentUser(), "system:user", new Object(), Set.of("username").stream().toList(), -1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.createTask(currentUser(), "../system", new Object(), Set.of("username").stream().toList(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.createTask(currentUser(), "system:user", new Object(), java.util.List.of("username", "../password"), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(exportTaskMapper, never()).insert(any(ExportTaskEntity.class));
    }

    @Test
    void createTaskShouldRejectMissingSessionVersionBeforeInsert() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());

        assertThatThrownBy(() -> service.createTask(missingSessionVersionUser(), "system:user", new Object(), Set.of("username").stream().toList(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(exportTaskMapper, never()).insert(any(ExportTaskEntity.class));
    }

    @Test
    void getTaskShouldRequireExportPermissionBeforeSelect() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());
        CurrentUser currentUser = currentUser();
        currentUser.setPermissions(Set.of("system:user:view"));

        assertThatThrownBy(() -> service.getTask(currentUser, 9001L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(exportTaskMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void getTaskShouldRejectInvalidTaskIdBeforeSelect() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());

        assertThatThrownBy(() -> service.getTask(currentUser(), 0L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(exportTaskMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void statusUpdatesShouldRejectInvalidTaskIdBeforeMapperUpdate() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());

        assertThatThrownBy(() -> service.markRunning(currentUser(), 0L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.markSuccess(currentUser(), 0L, fileObject(501L), "users.xlsx"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.markFailed(currentUser(), 0L, new RuntimeException("failed")))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(exportTaskMapper, never()).update(any(ExportTaskEntity.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void uploadExportFileShouldRejectBlankUsernameBeforeFileUpload() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.uploadExportFile(currentUser, new byte[]{1, 2, 3}, "users.xlsx", "user-export", "export,user", "report"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(fileInternalApi, never()).uploadDocumentForUser(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadExportFileShouldRejectInvalidFileBeforeFileUpload() {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        ExportTaskService service = new ExportTaskService(exportTaskMapper, fileInternalApi, new ObjectMapper());

        assertThatThrownBy(() -> service.uploadExportFile(currentUser(), new byte[0], "users.xlsx", "user-export", "export,user", "report"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.uploadExportFile(currentUser(), new byte[]{1}, " ", "user-export", "export,user", "report"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.uploadExportFile(currentUser(), new byte[]{1}, "../users.xlsx", "user-export", "export,user", "report"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.uploadExportFile(currentUser(), new byte[]{1}, "users.csv", "user-export", "export,user", "report"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.uploadExportFile(currentUser(), new byte[]{1}, "users.xlsx", "c".repeat(65), "export,user", "report"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.uploadExportFile(currentUser(), new byte[]{1}, "users.xlsx", "user-export", "export,user", "bad\u0000remark"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(fileInternalApi, never()).uploadDocumentForUser(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("alice");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of("system:user:export"));
        return currentUser;
    }

    private CurrentUser missingSessionVersionUser() {
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);
        return currentUser;
    }

    private FileObjectDTO fileObject(Long id) {
        return fileObject(id, 1001L);
    }

    private FileObjectDTO fileObject(Long id, Long uploadedBy) {
        return fileObject(id, uploadedBy, uploadedBy == null ? null : "user-uuid-" + uploadedBy);
    }

    private FileObjectDTO fileObject(Long id, Long uploadedBy, String uploadedByUuid) {
        return new FileObjectDTO(
                id,
                uploadedBy,
                uploadedByUuid,
                "alice",
                "users.xlsx",
                "users.xlsx",
                "LOCAL",
                null,
                "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                3L,
                "3 B",
                "storage/uploads/users.xlsx",
                null,
                null,
                null,
                "download",
                false,
                "user-export",
                "export,user",
                "report",
                "ENABLED",
                LocalDateTime.now(),
                LocalDateTime.now()
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
}
