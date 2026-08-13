package com.lumira.saas.modules.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.export.ExportTaskPort;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.security.TrustedUserSnapshotResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

class ExportTaskServiceTest {

    @Test
    void createTaskPersistsTheResolvedOwnerAndReturnsOnlyTaskReference() {
        ExportTaskMapper mapper = mock(ExportTaskMapper.class);
        TrustedCurrentUserResolver requestResolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser requestUser = trustedUser(Set.of("system:user:export"));
        CurrentUser resolvedUser = trustedUser(Set.of("system:user:export"));
        resolvedUser.setUserUuid("live-user-uuid");
        when(requestResolver.resolve(requestUser)).thenReturn(resolvedUser);
        when(mapper.insert(any(ExportTaskEntity.class))).thenAnswer(invocation -> {
            invocation.<ExportTaskEntity>getArgument(0).setId(7001L);
            return 1;
        });

        ExportTaskService service = new ExportTaskService(
                mapper,
                mock(FileInternalApi.class),
                new ObjectMapper(),
                requestResolver,
                mock(TrustedUserSnapshotResolver.class),
                true
        );

        ExportTaskPort.ExportTask task = service.createTask(
                requestUser,
                "system:user",
                List.of("request"),
                List.of("id", "username"),
                12L,
                "system:user:export"
        );

        ArgumentCaptor<ExportTaskEntity> entity = ArgumentCaptor.forClass(ExportTaskEntity.class);
        verify(mapper).insert(entity.capture());
        assertThat(task.id()).isEqualTo(7001L);
        assertThat(entity.getValue().getCreatedBy()).isEqualTo(1001L);
        assertThat(entity.getValue().getCreatedByUuid()).isEqualTo("live-user-uuid");
        assertThat(entity.getValue().getStatus()).isEqualTo(ExportTaskService.STATUS_PENDING);
    }

    @Test
    void trustedSnapshotStateTransitionUsesFreshOwnerSnapshot() {
        ExportTaskMapper mapper = mock(ExportTaskMapper.class);
        TrustedUserSnapshotResolver snapshotResolver = mock(TrustedUserSnapshotResolver.class);
        CurrentUser queuedUser = trustedUser(Set.of("system:user:export"));
        queuedUser.setSessionId("internal-export-task-7001");
        CurrentUser liveUser = trustedUser(Set.of("system:user:export"));
        liveUser.setUserUuid("live-user-uuid");
        when(snapshotResolver.resolve(
                eq(1001L),
                eq("user-uuid-1001"),
                eq(null),
                eq("internal-export-task-7001"),
                eq("system:user:export")
        )).thenReturn(liveUser);
        when(mapper.update(any(ExportTaskEntity.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        ExportTaskService service = new ExportTaskService(
                mapper,
                mock(FileInternalApi.class),
                new ObjectMapper(),
                mock(TrustedCurrentUserResolver.class),
                snapshotResolver,
                true
        );

        service.markRunningFromTrustedSnapshot(queuedUser, 7001L, "system:user:export");

        verify(snapshotResolver).resolve(
                1001L,
                "user-uuid-1001",
                null,
                "internal-export-task-7001",
                "system:user:export"
        );
        verify(mapper).update(any(ExportTaskEntity.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void requestResolverCannotGrantMissingExportPermission() {
        ExportTaskMapper mapper = mock(ExportTaskMapper.class);
        TrustedCurrentUserResolver requestResolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser requestUser = trustedUser(Set.of("system:user:view"));
        when(requestResolver.resolve(requestUser)).thenReturn(requestUser);
        ExportTaskService service = new ExportTaskService(
                mapper,
                mock(FileInternalApi.class),
                new ObjectMapper(),
                requestResolver,
                mock(TrustedUserSnapshotResolver.class),
                true
        );

        assertThatThrownBy(() -> service.createTask(
                requestUser,
                "system:user",
                List.of("request"),
                List.of("id"),
                1L,
                "system:user:export"
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void trustedUploadUsesTheFreshSnapshotAsTheFileOwner() {
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        TrustedUserSnapshotResolver snapshotResolver = mock(TrustedUserSnapshotResolver.class);
        CurrentUser queuedUser = trustedUser(Set.of("system:user:export"));
        CurrentUser liveUser = trustedUser(Set.of("system:user:export"));
        liveUser.setUserUuid("live-user-uuid");
        when(snapshotResolver.resolve(anyLong(), anyString(), any(), anyString(), eq("system:user:export")))
                .thenReturn(liveUser);
        FileObjectDTO uploaded = new FileObjectDTO(
                808L, 1001L, "live-user-uuid", "alice", "users.xlsx", "users.xlsx", "LOCAL", "local",
                "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 8L, "8 B",
                "/exports/users.xlsx", null, null, null, null, false, "user-export", "export,user",
                "report", "ACTIVE", LocalDateTime.now(), LocalDateTime.now()
        );
        when(fileInternalApi.uploadDocumentForUser(any(), any(), any(), any(), any(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(uploaded);
        ExportTaskService service = new ExportTaskService(
                mock(ExportTaskMapper.class),
                fileInternalApi,
                new ObjectMapper(),
                mock(TrustedCurrentUserResolver.class),
                snapshotResolver,
                true
        );

        FileObjectDTO result = service.uploadExportFileFromTrustedSnapshot(
                queuedUser,
                new byte[] {1, 2, 3},
                "users.xlsx",
                "user-export",
                "export,user",
                "report",
                "system:user:export"
        );

        assertThat(result.id()).isEqualTo(808L);
        verify(fileInternalApi).uploadDocumentForUser(
                any(), any(), any(), any(), any(), eq(1001L), eq("live-user-uuid"), eq("alice"), eq(null)
        );
    }

    @Test
    void trustedUploadAcceptsMaterialZipAndUsesZipContentType() {
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        TrustedUserSnapshotResolver snapshotResolver = mock(TrustedUserSnapshotResolver.class);
        CurrentUser queuedUser = trustedUser(Set.of("registration:material:download"));
        CurrentUser liveUser = trustedUser(Set.of("registration:material:download"));
        liveUser.setUserUuid("live-user-uuid");
        when(snapshotResolver.resolve(anyLong(), anyString(), any(), anyString(), eq("registration:material:download")))
                .thenReturn(liveUser);
        FileObjectDTO uploaded = new FileObjectDTO(
                809L, 1001L, "live-user-uuid", "alice", "materials.zip", "materials.zip", "LOCAL", "local",
                "zip", "application/zip", 8L, "8 B", "/exports/materials.zip", null, null, null, null, false,
                "competition-registration-materials", "export,competition,registration,materials", "material package",
                "ACTIVE", LocalDateTime.now(), LocalDateTime.now()
        );
        when(fileInternalApi.uploadDocumentForUser(any(), any(), any(), any(), any(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(uploaded);
        ExportTaskService service = new ExportTaskService(
                mock(ExportTaskMapper.class),
                fileInternalApi,
                new ObjectMapper(),
                mock(TrustedCurrentUserResolver.class),
                snapshotResolver,
                true
        );

        FileObjectDTO result = service.uploadExportFileFromTrustedSnapshot(
                queuedUser,
                new byte[] {1, 2, 3},
                "materials.zip",
                "competition-registration-materials",
                "export,competition,registration,materials",
                "material package",
                "registration:material:download"
        );

        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        verify(fileInternalApi).uploadDocumentForUser(
                file.capture(), any(), any(), any(), any(), eq(1001L), eq("live-user-uuid"), eq("alice"), eq(null)
        );
        assertThat(result.id()).isEqualTo(809L);
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("materials.zip");
        assertThat(file.getValue().getContentType()).isEqualTo("application/zip");
    }

    @Test
    void trustedSuccessStateAcceptsMaterialZipFileName() {
        ExportTaskMapper mapper = mock(ExportTaskMapper.class);
        TrustedUserSnapshotResolver snapshotResolver = mock(TrustedUserSnapshotResolver.class);
        CurrentUser queuedUser = trustedUser(Set.of("registration:material:download"));
        CurrentUser liveUser = trustedUser(Set.of("registration:material:download"));
        liveUser.setUserUuid("live-user-uuid");
        when(snapshotResolver.resolve(anyLong(), anyString(), any(), anyString(), eq("registration:material:download")))
                .thenReturn(liveUser);
        when(mapper.update(any(ExportTaskEntity.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        FileObjectDTO uploaded = new FileObjectDTO(
                809L, 1001L, "live-user-uuid", "alice", "materials.zip", "materials.zip", "LOCAL", "local",
                "zip", "application/zip", 8L, "8 B", "/exports/materials.zip", null, null, null, null, false,
                "competition-registration-materials", "export,competition,registration,materials", "material package",
                "ACTIVE", LocalDateTime.now(), LocalDateTime.now()
        );
        ExportTaskService service = new ExportTaskService(
                mapper,
                mock(FileInternalApi.class),
                new ObjectMapper(),
                mock(TrustedCurrentUserResolver.class),
                snapshotResolver,
                true
        );

        service.markSuccessFromTrustedSnapshot(
                queuedUser,
                7001L,
                uploaded,
                "materials.zip",
                "registration:material:download"
        );

        ArgumentCaptor<ExportTaskEntity> update = ArgumentCaptor.forClass(ExportTaskEntity.class);
        verify(mapper).update(update.capture(), any(LambdaUpdateWrapper.class));
        assertThat(update.getValue().getFileName()).isEqualTo("materials.zip");
        assertThat(update.getValue().getStatus()).isEqualTo(ExportTaskService.STATUS_SUCCESS);
    }

    @Test
    void trustedUploadRejectsUnsupportedExportExtensions() {
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        TrustedUserSnapshotResolver snapshotResolver = mock(TrustedUserSnapshotResolver.class);
        CurrentUser queuedUser = trustedUser(Set.of("system:user:export"));
        when(snapshotResolver.resolve(anyLong(), anyString(), any(), anyString(), eq("system:user:export")))
                .thenReturn(trustedUser(Set.of("system:user:export")));
        ExportTaskService service = new ExportTaskService(
                mock(ExportTaskMapper.class),
                fileInternalApi,
                new ObjectMapper(),
                mock(TrustedCurrentUserResolver.class),
                snapshotResolver,
                true
        );

        assertThatThrownBy(() -> service.uploadExportFileFromTrustedSnapshot(
                queuedUser,
                new byte[] {1},
                "unsafe.exe",
                "user-export",
                "export,user",
                "report",
                "system:user:export"
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.uploadExportFileFromTrustedSnapshot(
                queuedUser,
                new byte[] {1},
                "folder\\materials.zip",
                "user-export",
                "export,user",
                "report",
                "system:user:export"
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        verifyNoInteractions(fileInternalApi);
    }

    private CurrentUser trustedUser(Set<String> permissions) {
        CurrentUser user = new CurrentUser(1001L, "alice", "session-1", 1, true, permissions);
        user.setUserUuid("user-uuid-1001");
        user.setPermissionsVersion("permissions-1");
        return user;
    }
}
