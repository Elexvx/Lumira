package com.lumira.file.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.common.exception.BizException;
import com.lumira.file.event.FileOutboxRelay;
import com.lumira.file.processing.FileProcessingTaskService;
import org.junit.jupiter.api.Test;

class InternalJobControllerTest {

    @Test
    void processFileTasks_shouldDelegateToFileOwnerServiceWhenAuthorized() {
        FileOutboxRelay relay = mock(FileOutboxRelay.class);
        FileProcessingTaskService processingTaskService = mock(FileProcessingTaskService.class);
        when(processingTaskService.processPendingTasks(20)).thenReturn(3);
        var controller = new InternalJobController(relay, processingTaskService, "file-secret");

        var response = controller.processFileTasks(20, "file-secret");

        assertThat(response.getData()).isEqualTo(3);
        verify(processingTaskService).processPendingTasks(20);
    }

    @Test
    void processFileTasks_shouldRejectInvalidToken() {
        var controller = new InternalJobController(mock(FileOutboxRelay.class), mock(FileProcessingTaskService.class), "file-secret");

        assertThatThrownBy(() -> controller.processFileTasks(20, "bad"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void processFileTasks_shouldRejectOversizedTokenBeforeServiceCall() {
        FileProcessingTaskService processingTaskService = mock(FileProcessingTaskService.class);
        var controller = new InternalJobController(mock(FileOutboxRelay.class), processingTaskService, "file-secret");

        assertThatThrownBy(() -> controller.processFileTasks(20, "a".repeat(513)))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(processingTaskService);
    }

    @Test
    void processFileTasks_shouldRejectInvalidLimitBeforeServiceCall() {
        FileProcessingTaskService processingTaskService = mock(FileProcessingTaskService.class);
        var controller = new InternalJobController(mock(FileOutboxRelay.class), processingTaskService, "file-secret");

        assertThatThrownBy(() -> controller.processFileTasks(0, "file-secret"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> controller.processFileTasks(FileProcessingTaskService.MAX_CLAIM_LIMIT + 1, "file-secret"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(processingTaskService);
    }

    @Test
    void processFileTasks_shouldRejectGlobalTokenEvenWhenConfigured() {
        var controller = new InternalJobController(mock(FileOutboxRelay.class), mock(FileProcessingTaskService.class), "file-secret");

        assertThatThrownBy(() -> controller.processFileTasks(20, "global-secret"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void replayOutbox_shouldRejectInvalidIdBeforeRelayCall() {
        FileOutboxRelay relay = mock(FileOutboxRelay.class);
        var controller = new InternalJobController(relay, mock(FileProcessingTaskService.class), "file-secret");

        assertThatThrownBy(() -> controller.replayOutbox(0L, "file-secret"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(relay);
    }
}
