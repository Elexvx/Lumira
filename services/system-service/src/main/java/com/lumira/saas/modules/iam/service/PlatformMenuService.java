package com.lumira.saas.modules.iam.service;

import com.lumira.saas.modules.system.plugin.SystemPluginViewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Service
public class PlatformMenuService {

    private static final Logger log = LoggerFactory.getLogger(PlatformMenuService.class);

    private final MyBatisQueryOperations jdbcTemplate;
    private final SystemPluginViewService systemPluginViewService;

    public PlatformMenuService(MyBatisQueryOperations jdbcTemplate, SystemPluginViewService systemPluginViewService) {
        this.jdbcTemplate = jdbcTemplate;
        this.systemPluginViewService = systemPluginViewService;
    }

    public List<Map<String, Object>> buildTenantMenuTree(Long tenantId, List<String> permissions) {
        List<Map<String, Object>> flatMenus = new ArrayList<>(jdbcTemplate.query(
                """
                        select id, parent_id, menu_code, menu_name, path, component, icon, sort_no, permission_key
                        from sys_menu
                        where tenant_id = ?
                          and deleted = 0
                          and status = 'ENABLED'
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
        List<Map<String, Object>> pluginMenus;
        try {
            pluginMenus = systemPluginViewService.tenantPluginMenus(tenantId, permissions);
        } catch (Throwable throwable) {
            log.warn("Failed to load plugin menus tenantId={}", tenantId, throwable);
            pluginMenus = List.of();
        }
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
        flatMenus.sort(Comparator
                .comparingInt((Map<String, Object> item) -> safeInt(item.get("sortNo")))
                .thenComparingLong((Map<String, Object> item) -> safeLong(item.get("id"))));

        Set<Long> knownIds = new HashSet<>();
        Map<Long, List<Map<String, Object>>> childrenByParent = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> menu : flatMenus) {
            Long id = (Long) menu.get("id");
            Long parentId = (Long) menu.get("parentId");
            knownIds.add(id);
            childrenByParent.computeIfAbsent(parentId == null ? 0L : parentId, key -> new ArrayList<>()).add(menu);
        }
        for (Map<String, Object> menu : flatMenus) {
            Long parentId = (Long) menu.get("parentId");
            if (parentId == null || parentId == 0 || !knownIds.contains(parentId)) {
                roots.add(menu);
            }
        }

        Set<String> permissionSet = permissions == null ? Set.of() : new HashSet<>(permissions);
        List<Map<String, Object>> visibleRoots = new ArrayList<>();
        for (Map<String, Object> root : roots) {
            Map<String, Object> visibleRoot = pruneVisibleMenu(root, childrenByParent, permissionSet);
            if (visibleRoot != null) {
                visibleRoots.add(visibleRoot);
            }
        }
        return visibleRoots;
    }

    private Map<String, Object> pruneVisibleMenu(Map<String, Object> menu, Map<Long, List<Map<String, Object>>> childrenByParent, Set<String> permissions) {
        Long id = (Long) menu.get("id");
        List<Map<String, Object>> visibleChildren = new ArrayList<>();
        for (Map<String, Object> child : childrenByParent.getOrDefault(id, List.of())) {
            Map<String, Object> visibleChild = pruneVisibleMenu(child, childrenByParent, permissions);
            if (visibleChild != null) {
                visibleChildren.add(visibleChild);
            }
        }
        if (!isMenuAllowed(menu, permissions) && visibleChildren.isEmpty()) {
            return null;
        }
        Map<String, Object> visibleMenu = new LinkedHashMap<>(menu);
        visibleMenu.put("children", visibleChildren);
        return visibleMenu;
    }

    private boolean isMenuAllowed(Map<String, Object> menu, Set<String> permissions) {
        String permissionKey = (String) menu.get("permissionKey");
        return permissionKey == null || permissionKey.isBlank() || permissions.contains("*") || permissions.contains(permissionKey);
    }

    private int safeInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private long safeLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
