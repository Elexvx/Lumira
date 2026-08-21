package com.lumira.file.event;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class FilePlatformEventPublisherTest {

    @Test
    void publishUploadedShouldUseStandardFileEventKey() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FilePlatformEventPublisher publisher = publisher(outboxService);

        publisher.publishUploaded(currentUser(), file());

        verify(outboxService).record(
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_OBJECT_UPLOADED),
                eq(2001L),
                eq("FILE_OBJECT_UPLOADED:file.object:3001"),
                any()
        );
    }

    @Test
    void publishUploadedShouldUseCurrentUserWhenFileIsMissing() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FilePlatformEventPublisher publisher = publisher(outboxService);
        CurrentUser currentUser = currentUser();

        publisher.publishUploaded(currentUser, null);

        verify(outboxService).record(
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_OBJECT_UPLOADED),
                eq(2001L),
                eq("FILE_OBJECT_UPLOADED:file.object:none"),
                any()
        );
    }

    @Test
    void publishUploadedShouldIncludeTrustedUserUuidInPayload() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FilePlatformEventPublisher publisher = publisher(outboxService);
        ArgumentCaptor<Object> payloadCaptor = forClass(Object.class);

        publisher.publishUploaded(currentUser(), file());

        verify(outboxService).record(
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_OBJECT_UPLOADED),
                eq(2001L),
                eq("FILE_OBJECT_UPLOADED:file.object:3001"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue())
                .isInstanceOfSatisfying(Map.class, payload ->
                        assertThat(payload).containsEntry("userId", 2001L)
                                .containsEntry("userUuid", "user-uuid-2001"));
    }

    @Test
    void publishUploadedShouldIncludeSimulatedRoleIdInPayloadWhenPresent() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FilePlatformEventPublisher publisher = publisher(outboxService);
        ArgumentCaptor<Object> payloadCaptor = forClass(Object.class);
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(9L);

        publisher.publishUploaded(currentUser, file());

        verify(outboxService).record(
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_OBJECT_UPLOADED),
                eq(2001L),
                eq("FILE_OBJECT_UPLOADED:file.object:3001"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue())
                .isInstanceOfSatisfying(Map.class, payload ->
                        assertThat(payload).containsEntry("simulatedRoleId", 9L));
    }

    @Test
    void publishUploadedShouldRejectUnauthenticatedUserBeforeOutboxWrite() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FilePlatformEventPublisher publisher = publisher(outboxService);

        assertThatThrownBy(() -> publisher.publishUploaded(unauthenticatedUser(), file()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(outboxService);
    }

    @Test
    void publishUploadedShouldRejectBlankUsernameBeforeOutboxWrite() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FilePlatformEventPublisher publisher = publisher(outboxService);

        assertThatThrownBy(() -> publisher.publishUploaded(blankUsernameUser(), file()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(outboxService);
    }

    @Test
    void publishUploadedShouldRejectMissingUserUuidBeforeOutboxWrite() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FilePlatformEventPublisher publisher = publisher(outboxService);
        CurrentUser currentUser = currentUser();
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> publisher.publishUploaded(currentUser, file()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(outboxService);
    }

    @Test
    void publishUploadedShouldRejectDisabledTrustedUserBeforeOutboxWrite() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "DISABLED"));
        FilePlatformEventPublisher publisher = new FilePlatformEventPublisher(outboxService, provider(systemInternalApi));

        assertThatThrownBy(() -> publisher.publishUploaded(currentUser(), file()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(systemInternalApi).findUserIdentityById(2001L);
        verifyNoInteractions(outboxService);
    }

    @Test
    void publishUploadedShouldRejectTrustedUserUuidMismatchBeforeOutboxWrite() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "another-uuid", "ENABLED"));
        FilePlatformEventPublisher publisher = new FilePlatformEventPublisher(outboxService, provider(systemInternalApi));

        assertThatThrownBy(() -> publisher.publishUploaded(currentUser(), file()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(systemInternalApi).findUserIdentityById(2001L);
        verifyNoInteractions(outboxService);
    }

    @Test
    void buildEventKeyShouldFallbackForMissingFileId() {
        FilePlatformEventPublisher publisher = new FilePlatformEventPublisher(mock(PlatformEventOutboxService.class));

        assertEquals("FILE_OBJECT_DELETED:file.object:none",
                publisher.buildEventKey(FilePlatformEventTypes.FILE_OBJECT_DELETED, null));
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser(2001L, "tester", "session-1", 1, true, Set.of("system:file:upload"));
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(2001L, "tester", "session-1", 1, false, Set.of("system:file:upload"));
    }

    private CurrentUser blankUsernameUser() {
        return new CurrentUser(2001L, " ", "session-1", 1, true, Set.of("system:file:upload"));
    }

    private FilePlatformEventPublisher publisher(PlatformEventOutboxService outboxService) {
        return new FilePlatformEventPublisher(outboxService, provider(enabledSystemInternalApi()));
    }

    private SystemInternalApi enabledSystemInternalApi() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(eq(2001L)))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "ENABLED"));
        return systemInternalApi;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<SystemInternalApi> provider(SystemInternalApi systemInternalApi) {
        ObjectProvider<SystemInternalApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(systemInternalApi);
        return provider;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
                "tester",
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

    private FileObjectDTO file() {
        return new FileObjectDTO(
                3001L,
                2001L,
                "user-uuid-2001",
                "tester",
                "report.pdf",
                "2026/05/report.pdf",
                "LOCAL",
                "local",
                "pdf",
                "application/pdf",
                1024L,
                "1KB",
                "2026/05/report.pdf",
                "/api/uploads/2026/05/report.pdf",
                "/api/uploads/2026/05/report.pdf",
                "/api/uploads/2026/05/report.pdf",
                "PDF",
                true,
                "我的文件",
                "report",
                null,
                "ENABLED",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
