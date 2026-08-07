package com.lumira.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class LumiraAdminControlPlaneSchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LumiraAdminControlPlaneSchedulingConfiguration.class);

    @Test
    void controlPlaneRuntimeRegistersScheduledAnnotationProcessor() {
        contextRunner
                .withPropertyValues("lumira.runtime.control-plane-enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void runtimeAssemblyImportsControlPlaneScheduling() {
        Import imports = LumiraAdminRuntimeAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(imports.value()).contains(LumiraAdminControlPlaneSchedulingConfiguration.class);
    }

    @Test
    void disabledControlPlaneDoesNotRegisterScheduledAnnotationProcessor() {
        contextRunner
                .withPropertyValues("lumira.runtime.control-plane-enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
