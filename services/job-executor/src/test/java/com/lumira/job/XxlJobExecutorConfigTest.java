package com.lumira.job;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class XxlJobExecutorConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(XxlJobExecutorConfig.class)
            .withPropertyValues(
                    "xxl.job.admin.addresses=http://xxl-job-admin:8080/xxl-job-admin",
                    "xxl.job.accessToken=test-token",
                    "xxl.job.executor.appname=lumira-job-executor",
                    "xxl.job.executor.logpath=/tmp/xxl-job/logs"
            );

    @Test
    void xxlExecutorBeanShouldBeCreatedByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(XxlJobSpringExecutor.class));
    }

    @Test
    void xxlExecutorBeanShouldBeDisabledForRuntimeSmokeAndSplitDrills() {
        contextRunner
                .withPropertyValues("xxl.job.executor.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(XxlJobSpringExecutor.class));
    }
}
