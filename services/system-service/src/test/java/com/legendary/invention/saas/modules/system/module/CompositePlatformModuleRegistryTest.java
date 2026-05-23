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

        assertThat(modules.keySet()).contains("system");
        assertThat(modules.get("system").getLifecycleStatus()).isEqualTo("ENABLED");
        assertThat(modules.get("system").getSourceType()).isEqualTo("DATABASE");
        assertThat(modules.get("system").isOverriddenByDatabase()).isTrue();
        assertThat(modules.get("system").getRegistrationSourceOrder()).containsExactly("BUILTIN", "DATABASE");
        assertThat(modules.get("system").getRegisteredAt()).isEqualTo("2026-05-17T00:00:00");
        assertThat(modules.get("system").isDependencySatisfied()).isTrue();
    }

    @Test
    void shouldKeepStaticModulesWhenDatabaseIsEmpty() {
        CompositePlatformModuleRegistry registry = new CompositePlatformModuleRegistry(new StubDatabasePlatformModuleRepository(List.of()));

        assertThat(registry.findModule("system")).isPresent();
        assertThat(registry.findModule("system").orElseThrow().getRegistrationSourceOrder()).containsExactly("BUILTIN");
        assertThat(registry.findModule("missing")).isEmpty();
    }

    @Test
    void shouldIgnoreInvalidDatabaseModulesAndNormalizeNullableLists() {
        PlatformModuleVO invalid = databaseModule();
        invalid.setModuleCode(" ");
        PlatformModuleVO nullable = databaseModule();
        nullable.setModuleCode("custom-scene");
        nullable.setApiPrefixes(null);
        nullable.setPermissionKeys(null);
        nullable.setDependencies(null);
        nullable.setRegistrationSourceOrder(null);

        CompositePlatformModuleRegistry registry = new CompositePlatformModuleRegistry(new StubDatabasePlatformModuleRepository(List.of(invalid, nullable)));

        Map<String, PlatformModuleVO> modules = registry.listModules().stream()
                .collect(Collectors.toMap(PlatformModuleVO::getModuleCode, item -> item));

        assertThat(modules).doesNotContainKey(" ");
        assertThat(modules.get("custom-scene").getApiPrefixes()).isEmpty();
        assertThat(modules.get("custom-scene").getPermissionKeys()).isEmpty();
        assertThat(modules.get("custom-scene").getDependencies()).isEmpty();
        assertThat(modules.get("custom-scene").getRegistrationSourceOrder()).containsExactly("DATABASE");
    }

    private static PlatformModuleVO databaseModule() {
        PlatformModuleVO module = new PlatformModuleVO();
        module.setModuleCode("system");
        module.setModuleName("系统管理");
        module.setModuleType("FOUNDATION");
        module.setLifecycleStatus("ENABLED");
        module.setSourceType("DATABASE");
        module.setDescription("数据库注册的系统管理模块。");
        module.setOwnerService("system-service");
        module.setAdminRoutePath("/settings");
        module.setApiPrefixes(List.of("/api/v1/system/**"));
        module.setPermissionKeys(List.of("system:view"));
        module.setDependencies(List.of());
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
