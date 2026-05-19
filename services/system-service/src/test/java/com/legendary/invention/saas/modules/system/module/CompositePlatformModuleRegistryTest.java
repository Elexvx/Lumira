package com.legendary.invention.saas.modules.system.module;

import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CompositePlatformModuleRegistryTest {

    @Test
    void shouldMergeStaticAndDatabaseModules() {
        CompositePlatformModuleRegistry registry = new CompositePlatformModuleRegistry(new StubDatabasePlatformModuleRepository(List.of(databaseModule())));

        Map<String, PlatformModuleVO> modules = registry.listModules().stream()
                .collect(Collectors.toMap(PlatformModuleVO::getModuleCode, item -> item));

        assertThat(modules.keySet()).contains("system", "journal");
        assertThat(modules.get("journal").getLifecycleStatus()).isEqualTo("PLANNED");
        assertThat(modules.get("journal").getSourceType()).isEqualTo("DATABASE");
        assertThat(modules.get("journal").isOverriddenByDatabase()).isTrue();
        assertThat(modules.get("journal").getRegistrationSourceOrder()).containsExactly("BUILTIN", "DATABASE");
        assertThat(modules.get("journal").getRegisteredAt()).isEqualTo("2026-05-17T00:00:00");
        assertThat(modules.get("journal").isDependencySatisfied()).isFalse();
        assertThat(modules.get("journal").getInactiveDependencies()).contains("form", "submission");
    }

    @Test
    void shouldKeepStaticModulesWhenDatabaseIsEmpty() {
        CompositePlatformModuleRegistry registry = new CompositePlatformModuleRegistry(new StubDatabasePlatformModuleRepository(List.of()));

        assertThat(registry.findModule("system")).isPresent();
        assertThat(registry.findModule("system").orElseThrow().getRegistrationSourceOrder()).containsExactly("BUILTIN");
        assertThat(registry.findModule("missing")).isEmpty();
    }

    private static PlatformModuleVO databaseModule() {
        PlatformModuleVO module = new PlatformModuleVO();
        module.setModuleCode("journal");
        module.setModuleName("期刊场景");
        module.setModuleType("SCENE");
        module.setLifecycleStatus("PLANNED");
        module.setSourceType("DATABASE");
        module.setDescription("数据库注册的期刊场景模块。");
        module.setOwnerService("system-service");
        module.setAdminRoutePath("/journal");
        module.setApiPrefixes(List.of("/api/v1/journal/**"));
        module.setPermissionKeys(List.of("journal:view"));
        module.setDependencies(List.of("form", "submission", "approval", "evaluation", "file", "message", "site"));
        module.setRegistrationSourceOrder(List.of("DATABASE"));
        module.setRegisteredAt("2026-05-17T00:00:00");
        module.setBuiltin(false);
        return module;
    }

    private static class StubDatabasePlatformModuleRepository extends DatabasePlatformModuleRepository {

        private final List<PlatformModuleVO> modules;

        StubDatabasePlatformModuleRepository(List<PlatformModuleVO> modules) {
            super(null, null);
            this.modules = modules;
        }

        @Override
        public List<PlatformModuleVO> listModules() {
            return modules;
        }
    }
}
