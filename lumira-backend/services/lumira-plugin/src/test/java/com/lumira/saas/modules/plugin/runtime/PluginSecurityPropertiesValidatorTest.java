package com.lumira.saas.modules.plugin.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginSecurityPropertiesValidatorTest {
    @Test
    void productionRejectsInProcessBackendPlugins() {
        PluginProperties properties = new PluginProperties();
        properties.setSignatureSecret("a-production-secret-that-is-longer-than-thirty-two-characters");
        properties.setAllowInProcessBackendPlugins(true);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        assertThatThrownBy(() -> new PluginSecurityPropertiesValidator(properties, environment).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allow-in-process-backend-plugins=false");
    }
}
