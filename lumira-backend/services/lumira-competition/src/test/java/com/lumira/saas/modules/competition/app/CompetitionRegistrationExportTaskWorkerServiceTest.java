package com.lumira.saas.modules.competition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.export.ExportTaskPort;
import com.lumira.api.export.ExportTaskQueuePort;
import com.lumira.api.export.ExportTaskQueuePort.ExportTaskClaim;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompetitionRegistrationExportTaskWorkerServiceTest {

    @Test
    void processPendingTasksUploadsAndCompletesClaimedTask() throws Exception {
        ExportTaskQueuePort taskQueue = mock(ExportTaskQueuePort.class);
        CompetitionRegistrationExportAppService exportAppService =
                mock(CompetitionRegistrationExportAppService.class);
        ExportTaskPort exportTaskService = mock(ExportTaskPort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        CompetitionRegistrationExportTaskWorkerService worker =
                new CompetitionRegistrationExportTaskWorkerService(
                        taskQueue,
                        objectMapper,
                        exportAppService,
                        exportTaskService
                );
        CompetitionRegistrationDTO.RegistrationExportRequest request =
                new CompetitionRegistrationDTO.RegistrationExportRequest();
        request.setCompetitionId(88L);
        CompetitionRegistrationExportAppService.AsyncTaskPayload payload =
                new CompetitionRegistrationExportAppService.AsyncTaskPayload();
        payload.setRequest(request);
        payload.setFileName("competition-88.xlsx");
        when(taskQueue.claim(
                eq(CompetitionRegistrationExportAppService.MODULE_KEY),
                any(Integer.class),
                anyString(),
                anyString(),
                any(),
                any()
        ))
                .thenReturn(List.of(claim(objectMapper.writeValueAsString(payload))));
        CurrentUser user = trustedUser();
        when(exportAppService.buildQueuedAsyncUser(1001L, "user-uuid-1001", null, 9001L))
                .thenReturn(user);
        when(exportAppService.exportFromTrustedSnapshot(eq(user), any(), eq(9001L)))
                .thenReturn(new byte[]{1, 2, 3});
        when(exportTaskService.uploadExportFileFromTrustedSnapshot(
                eq(user),
                any(byte[].class),
                eq("competition-88.xlsx"),
                eq("competition-registration-export"),
                eq("export,competition,registration"),
                eq("competition registration dataset export"),
                eq(CompetitionRegistrationExportAppService.EXPORT_PERMISSION)
        )).thenReturn(uploadedFile());
        when(taskQueue.markSucceeded(any(), eq(501L), eq("competition-88.xlsx"), any()))
                .thenReturn(1);

        int processed = worker.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(taskQueue).markSucceeded(any(), eq(501L), eq("competition-88.xlsx"), any());
    }

    @Test
    void invalidPayloadIsFailedWithoutUploading() {
        ExportTaskQueuePort taskQueue = mock(ExportTaskQueuePort.class);
        CompetitionRegistrationExportAppService exportAppService =
                mock(CompetitionRegistrationExportAppService.class);
        ExportTaskPort exportTaskService = mock(ExportTaskPort.class);
        CompetitionRegistrationExportTaskWorkerService worker =
                new CompetitionRegistrationExportTaskWorkerService(
                        taskQueue,
                        new ObjectMapper(),
                        exportAppService,
                        exportTaskService
                );
        when(taskQueue.claim(
                eq(CompetitionRegistrationExportAppService.MODULE_KEY),
                any(Integer.class),
                anyString(),
                anyString(),
                any(),
                any()
        ))
                .thenReturn(List.of(claim("{bad-json")));
        when(taskQueue.markFailed(any(), anyString(), any())).thenReturn(1);

        int processed = worker.processPendingTasks(10);

        assertThat(processed).isZero();
        verify(taskQueue).markFailed(any(), anyString(), any());
        verify(exportAppService, never()).buildQueuedAsyncUser(any(), anyString(), any(), any());
        verifyNoInteractions(exportTaskService);
    }

    @Test
    void executionFailurePersistsTheBusinessReason() throws Exception {
        ExportTaskQueuePort taskQueue = mock(ExportTaskQueuePort.class);
        CompetitionRegistrationExportAppService exportAppService =
                mock(CompetitionRegistrationExportAppService.class);
        ExportTaskPort exportTaskService = mock(ExportTaskPort.class);
        CompetitionRegistrationExportTaskWorkerService worker =
                new CompetitionRegistrationExportTaskWorkerService(
                        taskQueue,
                        new ObjectMapper(),
                        exportAppService,
                        exportTaskService
                );
        CompetitionRegistrationExportAppService.AsyncTaskPayload payload =
                new CompetitionRegistrationExportAppService.AsyncTaskPayload();
        CompetitionRegistrationDTO.RegistrationExportRequest request =
                new CompetitionRegistrationDTO.RegistrationExportRequest();
        request.setCompetitionId(88L);
        payload.setRequest(request);
        payload.setFileName("competition-88.xlsx");
        ObjectMapper objectMapper = new ObjectMapper();
        when(taskQueue.claim(
                eq(CompetitionRegistrationExportAppService.MODULE_KEY),
                any(Integer.class),
                anyString(),
                anyString(),
                any(),
                any()
        )).thenReturn(List.of(claim(objectMapper.writeValueAsString(payload))));
        when(exportAppService.buildQueuedAsyncUser(1001L, "user-uuid-1001", null, 9001L))
                .thenThrow(new BizException(ErrorCode.FORBIDDEN, "Missing permission: registration:dataset:export"));
        when(taskQueue.markFailed(any(), eq("Missing permission: registration:dataset:export"), any())).thenReturn(1);

        int processed = worker.processPendingTasks(10);

        assertThat(processed).isZero();
        verify(taskQueue).markFailed(
                any(),
                eq("Missing permission: registration:dataset:export"),
                any()
        );
        verifyNoInteractions(exportTaskService);
    }

    @Test
    void materialPackageTaskUsesMaterialBuilderAndPermission() throws Exception {
        ExportTaskQueuePort taskQueue = mock(ExportTaskQueuePort.class);
        CompetitionRegistrationExportAppService exportAppService =
                mock(CompetitionRegistrationExportAppService.class);
        ExportTaskPort exportTaskService = mock(ExportTaskPort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        CompetitionRegistrationExportTaskWorkerService worker =
                new CompetitionRegistrationExportTaskWorkerService(
                        taskQueue,
                        objectMapper,
                        exportAppService,
                        exportTaskService
                );
        CompetitionRegistrationDTO.RegistrationExportRequest request =
                new CompetitionRegistrationDTO.RegistrationExportRequest();
        request.setCompetitionId(88L);
        CompetitionRegistrationExportAppService.AsyncTaskPayload payload =
                new CompetitionRegistrationExportAppService.AsyncTaskPayload();
        payload.setRequest(request);
        payload.setFileName("competition-88-materials.zip");
        payload.setExportType(CompetitionRegistrationExportAppService.EXPORT_TYPE_MATERIAL_ZIP);
        when(taskQueue.claim(
                eq(CompetitionRegistrationExportAppService.MODULE_KEY),
                any(Integer.class),
                anyString(),
                anyString(),
                any(),
                any()
        ))
                .thenReturn(List.of(claim(objectMapper.writeValueAsString(payload))));
        CurrentUser user = trustedUser();
        user.setPermissions(Set.of(
                CompetitionRegistrationExportAppService.EXPORT_PERMISSION,
                CompetitionRegistrationExportAppService.MATERIAL_DOWNLOAD_PERMISSION
        ));
        when(exportAppService.buildQueuedAsyncUser(1001L, "user-uuid-1001", null, 9001L))
                .thenReturn(user);
        when(exportAppService.exportMaterialPackageFromTrustedSnapshot(eq(user), any(), eq(9001L)))
                .thenReturn(new byte[]{4, 5, 6});
        when(exportTaskService.uploadExportFileFromTrustedSnapshot(
                eq(user),
                any(byte[].class),
                eq("competition-88-materials.zip"),
                eq("competition-registration-materials"),
                eq("export,competition,registration,materials"),
                eq("competition registration material package"),
                eq(CompetitionRegistrationExportAppService.MATERIAL_DOWNLOAD_PERMISSION)
        )).thenReturn(uploadedFile());
        when(taskQueue.markSucceeded(any(), eq(501L), eq("competition-88-materials.zip"), any()))
                .thenReturn(1);

        int processed = worker.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(exportAppService).exportMaterialPackageFromTrustedSnapshot(eq(user), any(), eq(9001L));
        verify(exportAppService, never()).exportFromTrustedSnapshot(any(), any(), any());
        verify(taskQueue).markSucceeded(any(), eq(501L), eq("competition-88-materials.zip"), any());
    }

    private ExportTaskClaim claim(String payload) {
        return new ExportTaskClaim(
                9001L,
                CompetitionRegistrationExportAppService.MODULE_KEY,
                "RUNNING",
                payload,
                1001L,
                "user-uuid-1001",
                "claim-token-1"
        );
    }

    private CurrentUser trustedUser() {
        CurrentUser user = new CurrentUser();
        user.setUserId(1001L);
        user.setUserUuid("user-uuid-1001");
        user.setUsername("operator");
        user.setSessionId("internal-registration-export-task-9001");
        user.setSessionVersion(1);
        user.setPermissionsVersion("permissions-2");
        user.setAuthenticated(true);
        user.setPermissions(Set.of(CompetitionRegistrationExportAppService.EXPORT_PERMISSION));
        return user;
    }

    private FileObjectDTO uploadedFile() {
        return new FileObjectDTO(
                501L,
                1001L,
                "user-uuid-1001",
                "operator",
                "competition-88.xlsx",
                "competition-88.xlsx",
                "LOCAL",
                null,
                "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                3L,
                "3 B",
                "storage/uploads/competition-88.xlsx",
                null,
                null,
                null,
                "download",
                false,
                "competition-registration-export",
                "export,competition,registration",
                "competition registration dataset export",
                "ENABLED",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
