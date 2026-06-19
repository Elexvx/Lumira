package com.lumira.saas.modules.system.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                if (normalized.contains("from sys_plugin_tenant")) {
                    List<PluginVO.TenantPluginVO> plugins = new ArrayList<>();
                    plugins.add(plugin("alpha", "Alpha", "1.0.0", alphaManifest));
                    plugins.add(plugin("beta", "Beta", "2.0.0", betaManifest));
                    return (List<T>) plugins;
                }
                if (normalized.contains("from sys_plugin_menu_rel")) {
                    Map<String, Object> menu = Map.of(
                            "pluginCode", "alpha",
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

        SystemPluginViewService service = new SystemPluginViewService(jdbcTemplate, new ObjectMapper());
        List<PluginVO.TenantPluginVO> plugins = service.availablePlugins(1001L);

        assertThat(plugins).extracting(PluginVO.TenantPluginVO::getPluginCode).containsExactly("alpha", "beta");
        assertThat(plugins.get(0).getMenus()).hasSize(1);
        assertThat(plugins.get(0).getSharedDeps()).containsExactly("shared-a");
        assertThat(plugins.get(1).getSharedDeps()).containsExactly("shared-b");
        assertThat(plugins.get(0).getRoutes()).containsExactly("/alpha");
        assertThat(plugins.get(1).getRoutes()).containsExactly("/beta");
    }

    private Path writeManifest(String name, List<String> sharedDeps, List<String> routes) throws Exception {
        Path path = tempDir.resolve(name);
        PluginDTO.FrontendPluginManifest manifest = new PluginDTO.FrontendPluginManifest();
        manifest.setSharedDeps(sharedDeps);
        manifest.setRoutes(routes);
        new ObjectMapper().writeValue(path.toFile(), manifest);
        return path;
    }

    private PluginVO.TenantPluginVO plugin(String code, String name, String version, Path manifestPath) {
        PluginVO.TenantPluginVO plugin = new PluginVO.TenantPluginVO();
        plugin.setPluginCode(code);
        plugin.setPluginName(name);
        plugin.setVersion(version);
        plugin.setManifestPath(manifestPath.toString());
        return plugin;
    }
}
