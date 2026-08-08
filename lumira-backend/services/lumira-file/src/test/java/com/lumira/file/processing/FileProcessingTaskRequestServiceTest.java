package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.file.event.FilePlatformEventTypes;
import com.lumira.file.event.PlatformEventOutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

class FileProcessingTaskRequestServiceTest {

    @Test
    void requestTasksForUploadShouldNotExposeNumericOnlyUserIdOperation() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);

        assertThat(Arrays.stream(FileProcessingTaskRequestService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(FileProcessingTaskRequestService.class))
                .map(Method::toString)
                .filter(signature -> signature.contains("requestTasksForUpload")
                        && signature.contains("FileObjectDTO,java.lang.Long"))
                .toList())
                .isEmpty();

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(outboxService);
    }

    @Test
    void requestTasksForUploadShouldIncludeTrustedUserUuidInOutboxPayload() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(3001L), eq(2001L), eq("user-uuid-2001")))
                .thenReturn(2001L);
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(1);
        FileProcessingTaskRequestService service = service(jdbcTemplate, outboxService);
        ArgumentCaptor<Object> payloadCaptor = forClass(Object.class);

        int requested = service.requestTasksForUpload(file("pdf", "application/pdf"), currentUser());

        assertThat(requested).isEqualTo(3);
        verify(outboxService, times(3)).recordAfterCommit(
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_PROCESSING_TASK_REQUESTED),
                eq(2001L),
                anyString(),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getAllValues())
                .allSatisfy(payload -> assertThat(payload)
                        .isInstanceOfSatisfying(Map.class, item ->
                                assertThat(item).containsEntry("userId", 2001L)
                                        .containsEntry("userUuid", "user-uuid-2001")));
    }

    @Test
    void requestTasksForCleanUploadShouldSkipDuplicateSecurityScanTask() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(3001L), eq(2001L), eq("user-uuid-2001")))
                .thenReturn(2001L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        int requested = service(jdbcTemplate, outboxService)
                .requestTasksForUpload(file(2001L, "pdf", "application/pdf", "CLEAN"), currentUser());

        assertThat(requested).isEqualTo(2);
        verify(outboxService, never()).recordAfterCommit(
                anyString(), anyString(), anyLong(), contains("SECURITY_SCAN"), any());
    }

    @Test
    void requestTasksForUploadShouldUseSimulatedRolePermissionSnapshotWhenPresent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        SystemInternalApi systemInternalApi = enabledSystemInternalApi();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(3001L), eq(2001L), eq("user-uuid-2001")))
                .thenReturn(2001L);
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(1);
        FileProcessingTaskRequestService service = new FileProcessingTaskRequestService(jdbcTemplate, outboxService, provider(systemInternalApi));
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(9L);
        ArgumentCaptor<Object> payloadCaptor = forClass(Object.class);

        int requested = service.requestTasksForUpload(file("txt", "text/plain"), currentUser);

        assertThat(requested).isEqualTo(3);
        verify(systemInternalApi).simulatedRolePermissionSnapshot(2001L, "user-uuid-2001", 9L);
        verify(systemInternalApi, org.mockito.Mockito.never()).permissionSnapshot(2001L, "user-uuid-2001");
        verify(outboxService, times(3)).recordAfterCommit(
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_PROCESSING_TASK_REQUESTED),
                eq(2001L),
                anyString(),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getAllValues())
                .allSatisfy(payload -> assertThat(payload)
                        .isInstanceOfSatisfying(Map.class, item ->
                                assertThat(item).containsEntry("simulatedRoleId", 9L)));
    }

    @Test
    void requestTasksForUploadShouldRejectMissingCurrentUserBeforeQueueWrite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FileProcessingTaskRequestService service = service(jdbcTemplate, outboxService);

        assertThatThrownBy(() -> service.requestTasksForUpload(file("txt", "text/plain"), (CurrentUser) null))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(outboxService);
    }

    @Test
    void requestTasksForUploadShouldRejectMismatchedExplicitUserIdBeforeQueueWrite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FileProcessingTaskRequestService service = service(jdbcTemplate, outboxService);

        CurrentUser currentUser = currentUser();
        currentUser.setUserId(9001L);
        currentUser.setUserUuid("user-uuid-9001");

        int requested = service.requestTasksForUpload(file("txt", "text/plain"), currentUser);

        assertThat(requested).isZero();
        verifyNoInteractions(outboxService);
    }

    @Test
    void requestTasksForUploadShouldRejectDisabledTrustedOwnerBeforeQueueWrite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L)).thenReturn(userSnapshot(2001L, "tester", "DISABLED"));
        FileProcessingTaskRequestService service = new FileProcessingTaskRequestService(jdbcTemplate, outboxService, provider(systemInternalApi));

        assertThatThrownBy(() -> service.requestTasksForUpload(file("txt", "text/plain"), currentUser()))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(outboxService);
    }

    @Test
    void requestTasksForUploadShouldRequireDatabaseOwnerUuidMatchBeforeQueueWrite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FileProcessingTaskRequestService service = service(jdbcTemplate, outboxService);

        int requested = service.requestTasksForUpload(file("txt", "text/plain"), currentUser());

        assertThat(requested).isZero();
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), eq(3001L), eq(2001L), eq("user-uuid-2001"));
        verify(outboxService, times(0)).recordAfterCommit(anyString(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void requestTasksForUploadShouldAllowPendingScanFileBeforeQueueWrite() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/file/processing/FileProcessingTaskRequestService.java"));

        assertThat(source)
                .contains("where fo.id = ?")
                .contains("and fo.deleted = 0")
                .contains("and fo.status in ('PENDING_SCAN', 'FAILED', 'ENABLED', 'CLEAN')")
                .contains("and fo.uploaded_by = ?")
                .contains("and fo.uploaded_by_uuid = ?");
    }

    @Test
    void duplicateTaskRequestShouldNotRewriteOriginalOwnerUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/file/processing/FileProcessingTaskRequestService.java"));

        assertThat(source)
                .contains("on duplicate key update")
                .contains("when created_by = values(created_by)")
                .contains("and created_by_uuid = values(created_by_uuid)")
                .doesNotContain("created_by_uuid = values(created_by_uuid),");
    }

    @Test
    void requestTasksForUploadShouldNotTrustExplicitUserIdWhenFileOwnerIsMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FileProcessingTaskRequestService service = service(jdbcTemplate, outboxService);

        int requested = service.requestTasksForUpload(file(null, "txt", "text/plain"), currentUser());

        assertThat(requested).isZero();
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(outboxService);
    }

    private FileObjectDTO file(String extension, String mimeType) {
        return file(2001L, extension, mimeType);
    }

    private FileObjectDTO file(Long uploadedBy, String extension, String mimeType) {
        return file(uploadedBy, extension, mimeType, "UPLOADED");
    }

    private FileObjectDTO file(Long uploadedBy, String extension, String mimeType, String status) {
        return new FileObjectDTO(
                3001L,
                uploadedBy,
                uploadedBy == null ? null : "user-uuid-" + uploadedBy,
                "admin",
                "sample." + extension,
                "stored-" + extension,
                "LOCAL",
                "local",
                extension,
                mimeType,
                1024L,
                "1 KB",
                "storage/uploads/sample." + extension,
                null,
                null,
                null,
                null,
                Boolean.TRUE,
                "GENERAL",
                null,
                null,
                status,
                null,
                null
        );
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser(2001L, "tester", null, "session-1", 1, true, Set.of("system:file:upload"));
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private FileProcessingTaskRequestService service(JdbcTemplate jdbcTemplate, PlatformEventOutboxService outboxService) {
        return new FileProcessingTaskRequestService(jdbcTemplate, outboxService, provider(enabledSystemInternalApi()));
    }

    private SystemInternalApi enabledSystemInternalApi() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> {
                    Long userId = invocation.getArgument(0, Long.class);
                    return userSnapshot(userId, "tester", "ENABLED");
                });
        return systemInternalApi;
    }

    private ObjectProvider<SystemInternalApi> provider(SystemInternalApi systemInternalApi) {
        if (systemInternalApi != null) {
            when(systemInternalApi.permissionSnapshot(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> permissionSnapshot(invocation.getArgument(0, Long.class)));
            when(systemInternalApi.simulatedRolePermissionSnapshot(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString(), ArgumentMatchers.anyLong()))
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
                List.of("system:file:upload"),
                List.of(31L),
                41L,
                List.of(41L),
                List.of(41L, 42L),
                List.of(),
                "/files"
        );
    }
}
