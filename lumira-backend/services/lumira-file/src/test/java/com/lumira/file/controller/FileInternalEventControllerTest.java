package com.lumira.file.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventPayloadDigests;
import com.lumira.api.file.FileObjectUploadedEventCommand;
import com.lumira.common.exception.BizException;
import com.lumira.file.event.FileEventApplicationService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileInternalEventControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsTheScopedFileTokenAndRuntimeReleaseHeader() {
        FileEventApplicationService service = mock(FileEventApplicationService.class);
        FileInternalEventController controller = new FileInternalEventController(service, "file-token");
        FileObjectUploadedEventCommand command = command();
        when(service.handleFileObjectUploaded(command)).thenReturn(true);

        var response = controller.handleFileObjectUploaded(
                command,
                "file-token",
                command.eventId(),
                command.producer(),
                FileEventApplicationService.CONSUMER_NAME,
                "async-release"
        );

        assertThat(response.getData()).isTrue();
        verify(service).handleFileObjectUploaded(command);
    }

    @Test
    void rejectsTheJobTokenAtTheFileOwnerBoundary() {
        FileEventApplicationService service = mock(FileEventApplicationService.class);
        FileInternalEventController controller = new FileInternalEventController(service, "file-token");
        FileObjectUploadedEventCommand command = command();

        assertThatThrownBy(() -> controller.handleFileObjectUploaded(
                command,
                "job-token",
                command.eventId(),
                command.producer(),
                FileEventApplicationService.CONSUMER_NAME,
                "async-release"
        )).isInstanceOf(BizException.class);
        verify(service, never()).handleFileObjectUploaded(command);
    }

    @Test
    void requiresTheRuntimeReleaseHeader() {
        FileEventApplicationService service = mock(FileEventApplicationService.class);
        FileInternalEventController controller = new FileInternalEventController(service, "file-token");
        FileObjectUploadedEventCommand command = command();

        assertThatThrownBy(() -> controller.handleFileObjectUploaded(
                command,
                "file-token",
                command.eventId(),
                command.producer(),
                FileEventApplicationService.CONSUMER_NAME,
                null
        )).isInstanceOf(BizException.class);
        verify(service, never()).handleFileObjectUploaded(command);
    }

    private FileObjectUploadedEventCommand command() {
        Map<String, Object> payload = Map.of("fileId", 100L, "name", "report.pdf");
        try {
            return new FileObjectUploadedEventCommand(
                    "file-event-controller-1",
                    FileEventApplicationService.FILE_OBJECT_UPLOADED,
                    "file",
                    "file",
                    "lumira-file",
                    "100",
                    1L,
                    1,
                    Instant.parse("2026-09-07T00:00:00Z"),
                    "trace-file-controller",
                    "release-test",
                    EventPayloadDigests.sha256(objectMapper.writeValueAsString(payload)),
                    payload
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
