package com.lumira.saas.modules.export.controller.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.lumira.api.export.UserExportTaskWorkerPort;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class InternalUserExportJobControllerTest {
    private static final String TOKEN = "job-token";

    @Test
    void processExportTasksInvokesTheSharedUserRenderer() {
        UserExportTaskWorkerPort worker = mock(UserExportTaskWorkerPort.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userExportTaskWorkerService", worker);
        InternalUserExportJobController controller = new InternalUserExportJobController(
                beans.getBeanProvider(UserExportTaskWorkerPort.class),
                TOKEN
        );

        controller.processExportTasks(20, TOKEN);

        verify(worker).processPendingTasks(20);
    }

    @Test
    void processExportTasksRejectsAnInvalidTokenOrLimit() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        InternalUserExportJobController controller = new InternalUserExportJobController(
                beans.getBeanProvider(UserExportTaskWorkerPort.class),
                TOKEN
        );

        assertThatThrownBy(() -> controller.processExportTasks(20, "wrong-token"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> controller.processExportTasks(0, TOKEN))
                .isInstanceOf(BizException.class);
    }
}
