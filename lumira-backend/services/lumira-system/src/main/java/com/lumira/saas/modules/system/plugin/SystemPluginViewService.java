package com.lumira.saas.modules.system.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemPluginViewService {

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedManifest> manifestCache = new ConcurrentHashMap<>();

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
        return plugins.parallelStream()
                .map(this::enrichPlugin)
                .toList();
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
            CachedManifest manifest = loadCachedManifest(Path.of(plugin.getManifestPath()));
            plugin.setSharedDeps(manifest.sharedDeps());
            plugin.setRoutes(manifest.routes());
        } catch (Exception exception) {
            plugin.setSharedDeps(List.of());
            plugin.setRoutes(List.of());
        }
    }

    private PluginVO.TenantPluginVO enrichPlugin(PluginVO.TenantPluginVO plugin) {
        plugin.setMenus(buildPluginMenus(plugin.getPluginCode(), plugin.getVersion()));
        loadFrontendManifest(plugin);
        return plugin;
    }

    private CachedManifest loadCachedManifest(Path manifestPath) throws java.io.IOException {
        String cacheKey = manifestPath.toAbsolutePath().normalize().toString();
        long modifiedAt = Files.getLastModifiedTime(manifestPath).toMillis();
        CachedManifest cached = manifestCache.get(cacheKey);
        if (cached != null && cached.modifiedAt() == modifiedAt) {
            return cached;
        }
        PluginDTO.FrontendPluginManifest manifest = objectMapper.readValue(manifestPath.toFile(), PluginDTO.FrontendPluginManifest.class);
        CachedManifest next = new CachedManifest(
                modifiedAt,
                immutableList(manifest.getSharedDeps()),
                immutableList(manifest.getRoutes())
        );
        manifestCache.put(cacheKey, next);
        return next;
    }

    private static List<String> immutableList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
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

    private record CachedManifest(long modifiedAt, List<String> sharedDeps, List<String> routes) {
    }
}
