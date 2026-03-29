package com.yourcompany.saas.modules.iam.service;

import com.yourcompany.saas.modules.plugin.app.PluginManagementAppService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformMenuService {

    private final JdbcTemplate jdbcTemplate;
    private final PluginManagementAppService pluginManagementAppService;

    public PlatformMenuService(JdbcTemplate jdbcTemplate, PluginManagementAppService pluginManagementAppService) {
        this.jdbcTemplate = jdbcTemplate;
        this.pluginManagementAppService = pluginManagementAppService;
    }

    public List<Map<String, Object>> buildTenantMenuTree(Long tenantId, List<String> permissions) {
        List<Map<String, Object>> flatMenus = new ArrayList<>(jdbcTemplate.query(
                """
                        select id, parent_id, menu_code, menu_name, path, component, icon, sort_no, permission_key
                        from sys_menu
                        where tenant_id = ?
                          and deleted = 0
                        order by sort_no asc, id asc
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("parentId", rs.getLong("parent_id"));
                    item.put("menuCode", rs.getString("menu_code"));
                    item.put("name", rs.getString("menu_name"));
                    item.put("path", rs.getString("path"));
                    item.put("component", rs.getString("component"));
                    item.put("icon", rs.getString("icon"));
                    item.put("sortNo", rs.getInt("sort_no"));
                    item.put("permissionKey", rs.getString("permission_key"));
                    item.put("children", new ArrayList<Map<String, Object>>());
                    return item;
                },
                tenantId
        ));
        List<Map<String, Object>> pluginMenus = pluginManagementAppService.tenantPluginMenus(tenantId, permissions);
        long seed = -1L;
        for (Map<String, Object> pluginMenu : pluginMenus) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", seed--);
            item.put("parentId", 0L);
            item.put("menuCode", pluginMenu.get("menuCode"));
            item.put("name", pluginMenu.get("name"));
            item.put("path", pluginMenu.get("path"));
            item.put("component", "PLUGIN");
            item.put("icon", pluginMenu.get("icon"));
            item.put("sortNo", pluginMenu.get("sortNo"));
            item.put("permissionKey", pluginMenu.get("permissionKey"));
            item.put("pluginCode", pluginMenu.get("pluginCode"));
            item.put("children", new ArrayList<Map<String, Object>>());
            flatMenus.add(item);
        }
        Map<Long, Map<String, Object>> index = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> menu : flatMenus) {
            String permissionKey = (String) menu.get("permissionKey");
            if (permissionKey != null && !permissionKey.isBlank() && !permissions.contains(permissionKey)) {
                continue;
            }
            index.put((Long) menu.get("id"), menu);
        }
        for (Map<String, Object> menu : index.values()) {
            Long parentId = (Long) menu.get("parentId");
            if (parentId == null || parentId == 0 || !index.containsKey(parentId)) {
                roots.add(menu);
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) index.get(parentId).get("children");
            children.add(menu);
        }
        roots.sort(Comparator.comparingInt(item -> (Integer) item.getOrDefault("sortNo", 0)));
        return roots;
    }
}
