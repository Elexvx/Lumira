package com.legendary.invention.saas.modules.system.module;

import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleVO;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformModuleCatalogTest {

    @Test
    void shouldExposeCurrentFoundationAndCapabilityModules() {
        Map<String, PlatformModuleVO> modules = PlatformModuleCatalog.listModules().stream()
                .collect(Collectors.toMap(PlatformModuleVO::getModuleCode, item -> item));

        assertThat(modules.keySet())
                .contains("system", "auth", "file", "message", "site", "plugin");
        assertThat(modules.get("system").getModuleType()).isEqualTo("FOUNDATION");
        assertThat(modules.get("site").getDependencies()).contains("file", "message");
        assertThat(modules.get("plugin").getApiPrefixes()).contains("/api/v1/plugins/**", "/api/p/{pluginCode}/**");
    }

    @Test
    void shouldNotExposeRemovedSceneModules() {
        Map<String, PlatformModuleVO> modules = PlatformModuleCatalog.listModules().stream()
                .collect(Collectors.toMap(PlatformModuleVO::getModuleCode, item -> item));

        assertThat(modules).doesNotContainKeys("journal", "competition");
    }

    @Test
    void shouldEvaluateDependencyReadiness() {
        Map<String, PlatformModuleVO> modules = PlatformModuleCatalog.listModules().stream()
                .collect(Collectors.toMap(PlatformModuleVO::getModuleCode, item -> item));

        assertThat(modules.get("site").isDependencySatisfied()).isTrue();
        assertThat(modules.get("site").isReadyToEnable()).isTrue();
        assertThat(modules.get("submission").isDependencySatisfied()).isFalse();
        assertThat(modules.get("submission").getInactiveDependencies()).contains("form");
        assertThat(modules.get("submission").getReadinessIssues()).isNotEmpty();
    }

    @Test
    void shouldFindSingleModuleWithReadinessDetails() {
        PlatformModuleVO module = PlatformModuleCatalog.findModule("submission").orElseThrow();

        assertThat(module.getModuleCode()).isEqualTo("submission");
        assertThat(module.getInactiveDependencies()).contains("form");
        assertThat(PlatformModuleCatalog.findModule("missing")).isEmpty();
    }
}
