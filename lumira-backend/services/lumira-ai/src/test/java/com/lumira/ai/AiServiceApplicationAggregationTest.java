package com.lumira.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiServiceApplicationAggregationTest {

    @Test
    void compatibilityLauncherCannotCreateAStandaloneProductionRuntime() {
        assertThatThrownBy(() -> AiServiceApplication.main(new String[0]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin control-plane module")
                .hasMessageContaining("lumira-server");
    }
}
