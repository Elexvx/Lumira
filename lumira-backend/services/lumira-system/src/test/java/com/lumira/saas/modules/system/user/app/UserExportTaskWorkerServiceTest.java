package com.lumira.saas.modules.system.user.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.system.export.ExportDTO;
import com.lumira.saas.modules.system.export.ExportTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserExportTaskWorkerServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void processPendingTasksShouldUploadAndCompleteClaimedTask() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UserExportAppService userExportAppService = mock(UserExportAppService.class);
        ExportTaskService exportTaskService = mock(ExportTaskService.class);
        UserExportTaskWorkerService service = new UserExportTaskWorkerService(
                jdbcTemplate,
                objectMapper,
                userExportAppService,
                exportTaskService
        );
        UserExportAppService.AsyncTaskPayload payload = new UserExportAppService.AsyncTaskPayload();
        payload.setRequest(request(List.of("id", "username")));
        payload.setFileName("users.xlsx");
        String requestPayload = objectMapper.writeValueAsString(payload);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.query(
                anyString(),
                any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class)
        )).thenReturn(List.of(new UserExportTaskWorkerService.ClaimedTask(
                9001L,
                "system:user",
                "RUNNING",
                requestPayload,
                1001L,
                "user-uuid-1001",
                "claim-token-1"
        )));
        CurrentUser currentUser = trustedUser();
        when(userExportAppService.buildQueuedAsyncUser(1001L, "user-uuid-1001", null, 9001L)).thenReturn(currentUser);
        when(userExportAppService.exportUsersFromTrustedSnapshot(eq(currentUser), any(ExportDTO.UserExportRequest.class)))
                .thenReturn(new byte[]{1, 2, 3});
        when(exportTaskService.uploadExportFileFromTrustedSnapshot(
                eq(currentUser),
                any(byte[].class),
                eq("users.xlsx"),
                eq("user-export"),
                eq("export,user"),
                eq("system user export")
        )).thenReturn(uploadedFile());

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(userExportAppService).buildQueuedAsyncUser(1001L, "user-uuid-1001", null, 9001L);
        verify(userExportAppService).exportUsersFromTrustedSnapshot(eq(currentUser), any(ExportDTO.UserExportRequest.class));
        verify(exportTaskService).uploadExportFileFromTrustedSnapshot(
                eq(currentUser),
                any(byte[].class),
                eq("users.xlsx"),
                eq("user-export"),
                eq("export,user"),
                eq("system user export")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void processPendingTasksShouldFailClaimedTaskWhenPayloadIsInvalid() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserExportAppService userExportAppService = mock(UserExportAppService.class);
        ExportTaskService exportTaskService = mock(ExportTaskService.class);
        UserExportTaskWorkerService service = new UserExportTaskWorkerService(
                jdbcTemplate,
                new ObjectMapper(),
                userExportAppService,
                exportTaskService
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.query(
                anyString(),
                any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class)
        )).thenReturn(List.of(new UserExportTaskWorkerService.ClaimedTask(
                9002L,
                "system:user",
                "RUNNING",
                "{bad-json",
                1001L,
                "user-uuid-1001",
                "claim-token-2"
        )));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isZero();
        verify(userExportAppService, never()).buildQueuedAsyncUser(any(), anyString(), any(), any());
        verifyNoInteractions(exportTaskService);
    }

    private ExportDTO.UserExportRequest request(List<String> fields) {
        ExportDTO.UserExportRequest request = new ExportDTO.UserExportRequest();
        request.setFields(fields);
        return request;
    }

    private CurrentUser trustedUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("operator");
        currentUser.setSessionId("internal-export-task-9001");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-2");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of("system:user:export"));
        return currentUser;
    }

    private FileObjectDTO uploadedFile() {
        return new FileObjectDTO(
                501L,
                1001L,
                "user-uuid-1001",
                "operator",
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
                "system user export",
                "ENABLED",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
