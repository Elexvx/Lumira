package com.lumira.server;

import com.lumira.saas.modules.activity.assembly.ActivityControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.competition.assembly.CompetitionControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.expert.assembly.ExpertControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.eventcatalog.assembly.EventCatalogControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.workflow.assembly.WorkflowControlPlaneAssemblyConfiguration;
import com.lumira.file.FileOwnerAsyncAdapterControlPlaneAssemblyConfiguration;
import com.lumira.message.MessageControlPlaneAssemblyConfiguration;
import com.lumira.message.MessageOwnerAsyncAdapterControlPlaneAssemblyConfiguration;
import com.lumira.payment.PaymentControlPlaneAssemblyConfiguration;
import com.lumira.payment.PaymentOwnerAsyncAdapterControlPlaneAssemblyConfiguration;
import com.lumira.plugin.PluginControlPlaneAssemblyConfiguration;
import com.lumira.plugin.PluginOwnerAsyncAdapterControlPlaneAssemblyConfiguration;
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
    void runtimeAssemblyExplicitlyImportsActivityContext() {
        Import imports = LumiraAdminRuntimeAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(imports.value()).contains(ActivityControlPlaneAssemblyConfiguration.class);
    }

    @Test
    void runtimeAssemblyExplicitlyImportsCompetitionContext() {
        Import imports = LumiraAdminRuntimeAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(imports.value()).contains(CompetitionControlPlaneAssemblyConfiguration.class);
    }

    @Test
    void runtimeAssemblyExplicitlyImportsReadOnlyEventCatalogContext() {
        Import imports = LumiraAdminRuntimeAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(imports.value()).contains(EventCatalogControlPlaneAssemblyConfiguration.class);
    }

    @Test
    void runtimeAssemblyExplicitlyImportsExpertContext() {
        Import imports = LumiraAdminRuntimeAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(imports.value()).contains(ExpertControlPlaneAssemblyConfiguration.class);
    }

    @Test
    void runtimeAssemblyExplicitlyImportsWorkflowContext() {
        Import imports = LumiraAdminRuntimeAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(imports.value()).contains(WorkflowControlPlaneAssemblyConfiguration.class);
    }

    @Test
    void controlPlaneOwnsTheRelayAdaptersThatTheAsyncRuntimeCallsRemotely() {
        assertThat(importsOf(LumiraFileControlPlaneAssemblyConfiguration.class))
                .contains(FileOwnerAsyncAdapterControlPlaneAssemblyConfiguration.class);
        assertThat(importsOf(MessageControlPlaneAssemblyConfiguration.class))
                .contains(MessageOwnerAsyncAdapterControlPlaneAssemblyConfiguration.class);
        assertThat(importsOf(PaymentControlPlaneAssemblyConfiguration.class))
                .contains(PaymentOwnerAsyncAdapterControlPlaneAssemblyConfiguration.class);
        assertThat(importsOf(PluginControlPlaneAssemblyConfiguration.class))
                .contains(PluginOwnerAsyncAdapterControlPlaneAssemblyConfiguration.class);
    }

    @Test
    void disabledControlPlaneDoesNotRegisterScheduledAnnotationProcessor() {
        contextRunner
                .withPropertyValues("lumira.runtime.control-plane-enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    private Class<?>[] importsOf(Class<?> configurationClass) {
        Import imports = configurationClass.getAnnotation(Import.class);
        assertThat(imports).isNotNull();
        return imports.value();
    }
}
