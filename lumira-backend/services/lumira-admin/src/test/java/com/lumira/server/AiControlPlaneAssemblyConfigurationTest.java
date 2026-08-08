package com.lumira.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.ai.assembly.AiControlPlaneAssemblyConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

class AiControlPlaneAssemblyConfigurationTest {

    @Test
    void adminRuntimeExplicitlyAggregatesTheAiControlPlane() {
        Import imports = LumiraAdminRuntimeAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(imports.value()).contains(AiControlPlaneAssemblyConfiguration.class);
    }
}
