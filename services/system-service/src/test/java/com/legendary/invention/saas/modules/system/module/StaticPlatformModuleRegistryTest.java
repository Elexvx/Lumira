package com.legendary.invention.saas.modules.system.module;

import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticPlatformModuleRegistryTest {

    private final PlatformModuleRegistry registry = new StaticPlatformModuleRegistry();

    @Test
    void shouldExposeCatalogThroughRegistryContract() {
        assertThat(registry.listModules())
                .extracting(PlatformModuleVO::getModuleCode)
                .contains("system", "site", "form", "submission")
                .doesNotContain("journal", "competition");
    }

    @Test
    void shouldFindModuleThroughRegistryContract() {
        assertThat(registry.findModule("submission"))
                .hasValueSatisfying(module -> {
                    assertThat(module.getModuleType()).isEqualTo("CAPABILITY");
                    assertThat(module.getReadinessIssues()).isNotEmpty();
                });
        assertThat(registry.findModule("missing")).isEmpty();
    }
}
