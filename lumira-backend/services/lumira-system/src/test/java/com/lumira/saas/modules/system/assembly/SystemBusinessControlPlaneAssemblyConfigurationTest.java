package com.lumira.saas.modules.system.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.modules.system.integration.workflow.SystemWorkflowIntegrationAssemblyConfiguration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

class SystemBusinessControlPlaneAssemblyConfigurationTest {

    @Test
    void explicitlyImportsSystemOwnedWorkflowAdapterComponents() {
        Import imported = SystemBusinessControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(Arrays.asList(imported.value()))
                .contains(SystemWorkflowIntegrationAssemblyConfiguration.class);
    }
}
