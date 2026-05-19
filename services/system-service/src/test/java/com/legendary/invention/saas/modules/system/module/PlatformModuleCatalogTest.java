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
                .contains("system", "auth", "file", "message", "approval", "evaluation", "site", "plugin");
        assertThat(modules.get("system").getModuleType()).isEqualTo("FOUNDATION");
        assertThat(modules.get("evaluation").getModuleType()).isEqualTo("CAPABILITY");
        assertThat(modules.get("site").getDependencies()).contains("file", "message");
        assertThat(modules.get("plugin").getApiPrefixes()).contains("/api/v1/plugins/**", "/api/p/{pluginCode}/**");
    }

    @Test
    void shouldMarkFutureSceneModulesAsPlanned() {
        Map<String, PlatformModuleVO> modules = PlatformModuleCatalog.listModules().stream()
                .collect(Collectors.toMap(PlatformModuleVO::getModuleCode, item -> item));

        assertThat(modules.get("journal").getModuleType()).isEqualTo("SCENE");
        assertThat(modules.get("journal").getLifecycleStatus()).isEqualTo("PLANNED");
        assertThat(modules.get("journal").isBuiltin()).isFalse();
        assertThat(modules.get("competition").getDependencies())
                .contains("form", "submission", "evaluation", "approval");
    }

    @Test
    void shouldEvaluateDependencyReadiness() {
        Map<String, PlatformModuleVO> modules = PlatformModuleCatalog.listModules().stream()
                .collect(Collectors.toMap(PlatformModuleVO::getModuleCode, item -> item));

        assertThat(modules.get("site").isDependencySatisfied()).isTrue();
        assertThat(modules.get("site").isReadyToEnable()).isTrue();
        assertThat(modules.get("journal").isDependencySatisfied()).isFalse();
        assertThat(modules.get("journal").getInactiveDependencies()).contains("form", "submission");
        assertThat(modules.get("journal").getReadinessIssues()).isNotEmpty();
    }

    @Test
    void shouldFindSingleModuleWithReadinessDetails() {
        PlatformModuleVO module = PlatformModuleCatalog.findModule("competition").orElseThrow();

        assertThat(module.getModuleCode()).isEqualTo("competition");
        assertThat(module.getInactiveDependencies()).contains("form", "submission");
        assertThat(PlatformModuleCatalog.findModule("missing")).isEmpty();
    }
}
