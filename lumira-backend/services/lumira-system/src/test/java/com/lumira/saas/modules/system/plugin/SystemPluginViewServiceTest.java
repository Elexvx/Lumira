package com.lumira.saas.modules.system.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemPluginViewServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void availablePluginsShouldPreserveOrderAndLoadManifests() throws Exception {
        Path alphaManifest = writeManifest("alpha.json", List.of("shared-a"), List.of("/alpha"));
        Path betaManifest = writeManifest("beta.json", List.of("shared-b"), List.of("/beta"));

        MyBatisQueryOperations jdbcTemplate = new MyBatisQueryOperations() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                String normalized = sql.toLowerCase();
                if (normalized.contains("from sys_plugin_definition")) {
                    assertThat(normalized).contains("join sys_plugin_version");
                    assertThat(normalized).contains("v.is_active = 1");
                    assertThat(args).isEmpty();
                    List<PluginVO.PluginAvailabilityVO> plugins = new ArrayList<>();
                    plugins.add(plugin("alpha", "Alpha", "1.0.0", alphaManifest));
                    plugins.add(plugin("beta", "Beta", "2.0.0", betaManifest));
                    return (List<T>) plugins;
                }
                if (normalized.contains("from sys_plugin_menu_rel")) {
                    Map<String, Object> alphaMenu = Map.of(
                            "pluginCode", "alpha",
                            "pluginVersion", "1.0.0",
                            "menuCode", "menu-1",
                            "parentMenuCode", "",
                            "name", "Menu One",
                            "path", "/alpha/menu",
                            "icon", "menu",
                            "permissionKey", "plugin:alpha:view",
                            "sortNo", 1
                    );
                    Map<String, Object> betaMenu = Map.of(
                            "pluginCode", "beta",
                            "pluginVersion", "2.0.0",
                            "menuCode", "menu-2",
                            "parentMenuCode", "",
                            "name", "Menu Two",
                            "path", "/beta/menu",
                            "icon", "menu",
                            "permissionKey", "plugin:beta:view",
                            "sortNo", 2
                    );
                    return (List<T>) List.of(alphaMenu, betaMenu);
                }
                return List.of();
            }
        };

        SystemPluginViewService service = new SystemPluginViewService(jdbcTemplate, new ObjectMapper());
        List<PluginVO.PluginAvailabilityVO> plugins = service.availablePlugins();

        assertThat(plugins).extracting(PluginVO.PluginAvailabilityVO::getPluginCode).containsExactly("alpha", "beta");
        assertThat(plugins.get(0).getMenus()).hasSize(1);
        assertThat(plugins.get(1).getMenus()).hasSize(1);
        assertThat(plugins.get(0).getSharedDeps()).containsExactly("shared-a");
        assertThat(plugins.get(1).getSharedDeps()).containsExactly("shared-b");
        assertThat(plugins.get(0).getRoutes()).containsExactly("/alpha");
        assertThat(plugins.get(1).getRoutes()).containsExactly("/beta");
    }

    @Test
    void availablePluginsShouldReuseCachedSnapshotUntilBootstrapVersionChanges() throws Exception {
        Path alphaManifest = writeManifest("alpha-cache.json", List.of("shared-a"), List.of("/alpha"));
        AtomicInteger definitionQueryCount = new AtomicInteger();
        AtomicInteger menuQueryCount = new AtomicInteger();
        AtomicInteger bootstrapVersion = new AtomicInteger(7);

        MyBatisQueryOperations jdbcTemplate = new MyBatisQueryOperations() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                String normalized = sql.toLowerCase();
                if (normalized.contains("from sys_plugin_definition")) {
                    definitionQueryCount.incrementAndGet();
                    List<PluginVO.PluginAvailabilityVO> plugins = new ArrayList<>();
                    plugins.add(plugin("alpha", "Alpha", "1.0.0", alphaManifest));
                    return (List<T>) plugins;
                }
                if (normalized.contains("from sys_plugin_menu_rel")) {
                    menuQueryCount.incrementAndGet();
                    Map<String, Object> menu = Map.of(
                            "pluginCode", "alpha",
                            "pluginVersion", "1.0.0",
                            "menuCode", "menu-1",
                            "parentMenuCode", "",
                            "name", "Menu One",
                            "path", "/alpha/menu",
                            "icon", "menu",
                            "permissionKey", "plugin:alpha:view",
                            "sortNo", 1
                    );
                    return (List<T>) List.of(menu);
                }
                return List.of();
            }
        };
        ReadModelVersionService readModelVersionService = new ReadModelVersionService(jdbcTemplate) {
            @Override
            public Long currentVersion(String contextName, String scope) {
                return (long) bootstrapVersion.get();
            }
        };

        SystemPluginViewService service = new SystemPluginViewService(jdbcTemplate, new ObjectMapper(), readModelVersionService);

        List<PluginVO.PluginAvailabilityVO> first = service.availablePlugins();
        List<PluginVO.PluginAvailabilityVO> second = service.availablePlugins();

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(definitionQueryCount.get()).isEqualTo(1);
        assertThat(menuQueryCount.get()).isEqualTo(1);

        bootstrapVersion.incrementAndGet();
        List<PluginVO.PluginAvailabilityVO> refreshed = service.availablePlugins();

        assertThat(refreshed).hasSize(1);
        assertThat(definitionQueryCount.get()).isEqualTo(2);
        assertThat(menuQueryCount.get()).isEqualTo(2);
    }

    @Test
    void availablePluginsShouldFilterUntrustedManifestEntries() throws Exception {
        Path alphaManifest = writeManifest(
                "alpha-untrusted.json",
                List.of("shared-a", " http://evil.example/script.js ", "../escape"),
                List.of("/alpha", "javascript:alert(1)", "../escape")
        );

        MyBatisQueryOperations jdbcTemplate = new MyBatisQueryOperations() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                String normalized = sql.toLowerCase();
                if (normalized.contains("from sys_plugin_definition")) {
                    List<PluginVO.PluginAvailabilityVO> plugins = new ArrayList<>();
                    plugins.add(plugin("alpha", "Alpha", "1.0.0", alphaManifest));
                    return (List<T>) plugins;
                }
                return List.of();
            }
        };

        SystemPluginViewService service = new SystemPluginViewService(jdbcTemplate, new ObjectMapper());

        List<PluginVO.PluginAvailabilityVO> plugins = service.availablePlugins();

        assertThat(plugins).hasSize(1);
        assertThat(plugins.get(0).getSharedDeps()).containsExactly("shared-a");
        assertThat(plugins.get(0).getRoutes()).containsExactly("/alpha");
    }

    @Test
    void availablePluginsShouldIgnoreInvalidManifestPath() throws Exception {
        Path invalidManifest = writeManifest("alpha.txt", List.of("shared-a"), List.of("/alpha"));

        MyBatisQueryOperations jdbcTemplate = new MyBatisQueryOperations() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                String normalized = sql.toLowerCase();
                if (normalized.contains("from sys_plugin_definition")) {
                    List<PluginVO.PluginAvailabilityVO> plugins = new ArrayList<>();
                    plugins.add(plugin("alpha", "Alpha", "1.0.0", invalidManifest));
                    return (List<T>) plugins;
                }
                return List.of();
            }
        };

        SystemPluginViewService service = new SystemPluginViewService(jdbcTemplate, new ObjectMapper());

        List<PluginVO.PluginAvailabilityVO> plugins = service.availablePlugins();

        assertThat(plugins).hasSize(1);
        assertThat(plugins.get(0).getSharedDeps()).isEmpty();
        assertThat(plugins.get(0).getRoutes()).isEmpty();
    }

    private Path writeManifest(String name, List<String> sharedDeps, List<String> routes) throws Exception {
        Path path = tempDir.resolve(name);
        PluginDTO.FrontendPluginManifest manifest = new PluginDTO.FrontendPluginManifest();
        manifest.setSharedDeps(sharedDeps);
        manifest.setRoutes(routes);
        new ObjectMapper().writeValue(path.toFile(), manifest);
        return path;
    }

    private PluginVO.PluginAvailabilityVO plugin(String code, String name, String version, Path manifestPath) {
        PluginVO.PluginAvailabilityVO plugin = new PluginVO.PluginAvailabilityVO();
        plugin.setPluginCode(code);
        plugin.setPluginName(name);
        plugin.setVersion(version);
        plugin.setManifestPath(manifestPath.toString());
        return plugin;
    }
}
