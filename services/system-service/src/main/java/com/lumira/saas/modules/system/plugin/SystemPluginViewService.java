package com.lumira.saas.modules.system.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemPluginViewService {

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SystemPluginViewService(MyBatisQueryOperations jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<PluginVO.TenantPluginVO> availablePlugins(Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        List<PluginVO.TenantPluginVO> plugins = jdbcTemplate.query(
                """
                        select d.plugin_code,
                               d.plugin_name,
                               t.plugin_version,
                               v.frontend_manifest_path
                        from sys_plugin_tenant t
                        join sys_plugin_definition d
                          on d.plugin_code = t.plugin_code
                         and d.deleted = 0
                        join sys_plugin_version v
                          on v.plugin_code = t.plugin_code
                         and v.version = t.plugin_version
                         and v.deleted = 0
                        where t.tenant_id = ?
                          and t.enabled = 1
                          and t.deleted = 0
                        order by d.sort_no asc, d.plugin_code asc
                        """,
                (rs, rowNum) -> {
                    PluginVO.TenantPluginVO vo = new PluginVO.TenantPluginVO();
                    vo.setPluginCode(rs.getString("plugin_code"));
                    vo.setPluginName(rs.getString("plugin_name"));
                    vo.setVersion(rs.getString("plugin_version"));
                    vo.setManifestPath(rs.getString("frontend_manifest_path"));
                    return vo;
                },
                tenantId
        );
        for (PluginVO.TenantPluginVO plugin : plugins) {
            plugin.setMenus(buildPluginMenus(plugin.getPluginCode(), plugin.getVersion()));
            loadFrontendManifest(plugin);
        }
        return plugins;
    }

    public List<Map<String, Object>> tenantPluginMenus(Long tenantId, List<String> permissions) {
        List<Map<String, Object>> menus = new ArrayList<>();
        List<String> permissionList = permissions == null ? List.of() : permissions;
        for (PluginVO.TenantPluginVO plugin : availablePlugins(tenantId)) {
            for (Map<String, Object> menu : plugin.getMenus()) {
                String permissionKey = (String) menu.get("permissionKey");
                if (!StringUtils.hasText(permissionKey) || permissionList.contains(permissionKey)) {
                    menus.add(menu);
                }
            }
        }
        return menus;
    }

    private void loadFrontendManifest(PluginVO.TenantPluginVO plugin) {
        if (!StringUtils.hasText(plugin.getManifestPath())) {
            plugin.setSharedDeps(List.of());
            plugin.setRoutes(List.of());
            return;
        }
        try {
            PluginDTO.FrontendPluginManifest manifest = objectMapper.readValue(
                    Path.of(plugin.getManifestPath()).toFile(),
                    PluginDTO.FrontendPluginManifest.class
            );
            plugin.setSharedDeps(manifest.getSharedDeps());
            plugin.setRoutes(manifest.getRoutes());
        } catch (Exception exception) {
            plugin.setSharedDeps(List.of());
            plugin.setRoutes(List.of());
        }
    }

    private List<Map<String, Object>> buildPluginMenus(String pluginCode, String version) {
        if (!StringUtils.hasText(pluginCode) || !StringUtils.hasText(version)) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        select plugin_code, plugin_version, menu_code, menu_name, route_path, icon, permission_key, parent_menu_code, sort_no
                        from sys_plugin_menu_rel
                        where plugin_code = ?
                          and plugin_version = ?
                          and deleted = 0
                        order by sort_no asc, id asc
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("pluginCode", rs.getString("plugin_code"));
                    item.put("menuCode", rs.getString("menu_code"));
                    item.put("parentMenuCode", rs.getString("parent_menu_code"));
                    item.put("name", rs.getString("menu_name"));
                    item.put("path", rs.getString("route_path"));
                    item.put("icon", rs.getString("icon"));
                    item.put("permissionKey", rs.getString("permission_key"));
                    item.put("sortNo", rs.getInt("sort_no"));
                    return item;
                },
                pluginCode,
                version
        );
    }
}
