package com.lumira.alerting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.alerting.app.AlertingJobService;
import com.lumira.alerting.model.AlertingModels;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;

class AlertingInternalJobControllerTest {
    @Test
    void runRequiresJobScopedInternalToken() {
        AlertingJobService service = mock(AlertingJobService.class);
        AlertingInternalJobController controller = new AlertingInternalJobController(service, "alert-job-token");

        assertThatThrownBy(() -> controller.run("wrong-token"))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        AlertingModels.JobRunResult result = new AlertingModels.JobRunResult(true, 2, 3, 2, 1);
        when(service.runOnce()).thenReturn(result);
        assertThat(controller.run("alert-job-token").getData()).isEqualTo(result);
        verify(service).runOnce();
    }

    @Test
    void runFailsClosedWhenTokenIsNotConfigured() {
        AlertingInternalJobController controller = new AlertingInternalJobController(mock(AlertingJobService.class), "");

        assertThatThrownBy(() -> controller.run(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("not configured");
    }
}
