package com.lumira.file.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        var controller = new InternalJobController(relay, processingTaskService, "secret");

        var response = controller.processFileTasks(20, "secret");

        assertThat(response.getData()).isEqualTo(3);
        verify(processingTaskService).processPendingTasks(20);
    }

    @Test
    void processFileTasks_shouldRejectInvalidToken() {
        var controller = new InternalJobController(mock(FileOutboxRelay.class), mock(FileProcessingTaskService.class), "secret");

        assertThatThrownBy(() -> controller.processFileTasks(20, "bad"))
                .isInstanceOf(BizException.class);
    }
}
