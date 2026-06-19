package com.lumira.file.event;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FilePlatformEventPublisherTest {

    @Test
    void publishUploadedAfterCommitShouldUseStandardFileEventKey() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FilePlatformEventPublisher publisher = new FilePlatformEventPublisher(outboxService);

        publisher.publishUploadedAfterCommit(currentUser(), file());

        verify(outboxService).recordAfterCommit(
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_OBJECT_UPLOADED),
                eq(1001L),
                eq(2001L),
                eq("FILE_OBJECT_UPLOADED:1001:file.object:3001"),
                any()
        );
    }

    @Test
    void buildEventKeyShouldFallbackForMissingFileId() {
        FilePlatformEventPublisher publisher = new FilePlatformEventPublisher(mock(PlatformEventOutboxService.class));

        assertEquals("FILE_OBJECT_DELETED:unknown:file.object:none",
                publisher.buildEventKey(FilePlatformEventTypes.FILE_OBJECT_DELETED, null, null));
    }

    private CurrentUser currentUser() {
        return new CurrentUser(2001L, "tester", 1001L, "session-1", 1, true, Set.of("system:file:upload"));
    }

    private FileObjectDTO file() {
        return new FileObjectDTO(
                3001L,
                1001L,
                2001L,
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
