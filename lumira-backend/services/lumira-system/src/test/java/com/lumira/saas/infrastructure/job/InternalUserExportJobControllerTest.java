package com.lumira.saas.infrastructure.job;

import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.system.user.app.UserExportTaskWorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalUserExportJobControllerTest {

    private static final String TOKEN = "job-token";

    @Test
    void processExportTasksRunsControlPlaneWorker() {
        UserExportTaskWorkerService worker = mock(UserExportTaskWorkerService.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("userExportTaskWorkerService", worker);
        InternalUserExportJobController controller = new InternalUserExportJobController(
                beanFactory.getBeanProvider(UserExportTaskWorkerService.class),
                TOKEN
        );

        controller.processExportTasks(20, TOKEN);

        verify(worker).processPendingTasks(20);
    }

    @Test
    void processExportTasksRejectsInvalidTokenBeforeWorkerCall() {
        UserExportTaskWorkerService worker = mock(UserExportTaskWorkerService.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("userExportTaskWorkerService", worker);
        InternalUserExportJobController controller = new InternalUserExportJobController(
                beanFactory.getBeanProvider(UserExportTaskWorkerService.class),
                TOKEN
        );

        assertThatThrownBy(() -> controller.processExportTasks(20, "wrong-token"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void processExportTasksRejectsInvalidLimit() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        InternalUserExportJobController controller = new InternalUserExportJobController(
                beanFactory.getBeanProvider(UserExportTaskWorkerService.class),
                TOKEN
        );

        assertThatThrownBy(() -> controller.processExportTasks(0, TOKEN))
                .isInstanceOf(BizException.class);
    }
}
