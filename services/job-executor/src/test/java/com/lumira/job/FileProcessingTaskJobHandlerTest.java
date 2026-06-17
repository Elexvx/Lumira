package com.lumira.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.xxl.job.core.handler.annotation.XxlJob;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class FileProcessingTaskJobHandlerTest {

    @Test
    void fileProcessingTaskJobShouldBeRegisteredAsPureAdapter() throws Exception {
        Method execute = FileProcessingTaskJobHandler.class.getDeclaredMethod("execute");
        XxlJob annotation = execute.getAnnotation(XxlJob.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("fileProcessingTaskJob");
        assertThat(FileProcessingTaskJobHandler.class.getDeclaredFields())
                .extracting(field -> field.getType().getSimpleName())
                .containsExactly("BackendJobClient");
    }
}
