package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.file.event.FilePlatformEventTypes;
import com.lumira.file.event.PlatformEventOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FileProcessingTaskRequestServiceTest {

    @Test
    void requestTasksForUploadShouldCreateExpectedTasksAndPublishEvents() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        when(jdbcTemplate.update(anyString(), anyLong(), anyString(), eq(FileProcessingTaskService.STATUS_PENDING), any(), anyLong(), anyLong()))
                .thenReturn(1);
        FileProcessingTaskRequestService service = new FileProcessingTaskRequestService(jdbcTemplate, outboxService);

        int requested = service.requestTasksForUpload(file("pdf", "application/pdf"), 2001L);

        assertThat(requested).isEqualTo(3);
        verify(jdbcTemplate, times(3)).update(anyString(), anyLong(), anyString(), eq(FileProcessingTaskService.STATUS_PENDING), any(), anyLong(), anyLong());
        verify(outboxService, times(3)).recordAfterCommit(
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_PROCESSING_TASK_REQUESTED),
                eq(2001L),
                anyString(),
                any()
        );
    }

    private FileObjectDTO file(String extension, String mimeType) {
        return new FileObjectDTO(
                3001L,
                2001L,
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
                "UPLOADED",
                null,
                null
        );
    }
}
