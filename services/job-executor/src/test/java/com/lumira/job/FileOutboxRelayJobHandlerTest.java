package com.lumira.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.xxl.job.core.handler.annotation.XxlJob;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class FileOutboxRelayJobHandlerTest {

    @Test
    void fileOutboxRelayJobShouldBeRegisteredAsPureAdapter() throws Exception {
        Method execute = FileOutboxRelayJobHandler.class.getDeclaredMethod("execute");
        XxlJob annotation = execute.getAnnotation(XxlJob.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("fileOutboxRelayJob");
        assertThat(FileOutboxRelayJobHandler.class.getDeclaredFields())
                .extracting(field -> field.getType().getSimpleName())
                .containsExactly("BackendJobClient");
    }
}
