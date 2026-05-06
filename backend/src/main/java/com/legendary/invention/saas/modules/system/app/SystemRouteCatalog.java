package com.legendary.invention.saas.modules.system.app;

import com.legendary.invention.saas.modules.system.vo.SystemVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SystemRouteCatalog {

    private static final Set<String> BUILT_IN_ROUTE_PATHS = Set.of(
            "/system",
            "/system/overview",
            "/system/menus",
            "/system/dicts",
            "/system/profile-fields",
            "/system/personalization",
            "/system/security",
            "/system/verification",
            "/system/smtp",
            "/system/notifications",
            "/system/plugins",
            "/system/monitoring",
            "/system/monitoring/service",
            "/system/monitoring/redis",
            "/system/monitoring/api-docs",
            "/system/monitoring/audit",
            "/system/files",
            "/system/files/my",
            "/system/files/all",
            "/files",
            "/files/all",
            "/settings",
            "/settings/overview",
            "/settings/menus",
            "/settings/dicts",
            "/settings/profile-fields",
            "/settings/personalization",
            "/settings/security",
            "/settings/verification",
            "/settings/smtp",
            "/settings/notifications",
            "/settings/plugins",
            "/settings/monitoring",
            "/settings/monitoring/service",
            "/settings/monitoring/redis",
            "/settings/monitoring/api-docs",
            "/settings/monitoring/audit",
            "/settings/files",
            "/settings/files/all"
    );

    private static final Set<String> BUILT_IN_COMPONENT_PATHS = Set.of(
            "@/pages/settings/menus",
            "@/pages/settings/dicts",
            "@/pages/settings/profile-fields",
            "@/pages/settings/personalization",
            "@/pages/settings/security",
            "@/pages/settings/verification",
            "@/pages/settings/notifications/index",
            "@/pages/settings/Plugins",
            "@/pages/settings/monitoring/index",
            "@/pages/settings/monitoring/ApiDocs",
            "@/pages/settings/monitoring/Audit",
            "@/pages/audit/Overview",
            "@/pages/system/menus",
            "@/pages/system/Plugins",
            "@/pages/system/dicts",
            "@/pages/system/profile-fields",
            "@/pages/system/personalization",
            "@/pages/system/security",
            "@/pages/system/verification",
            "@/pages/system/notifications/index",
            "@/pages/system/monitoring/index",
            "@/pages/system/monitoring/ApiDocs",
            "@/pages/system/monitoring/Audit"
    );

    private SystemRouteCatalog() {
    }

    public static boolean isBuiltInMenuPath(String path) {
      return path != null && BUILT_IN_ROUTE_PATHS.contains(path.trim());
    }

    public static boolean isBuiltInMenuComponent(String component) {
        return component != null && BUILT_IN_COMPONENT_PATHS.contains(component.trim());
    }

    public static boolean isBuiltInMenu(SystemVO.MenuVO menu) {
        if (menu == null) {
            return false;
        }
        return isBuiltInMenuPath(menu.getPath()) || isBuiltInMenuComponent(menu.getComponent());
    }

    public static List<SystemVO.MenuVO> buildBuiltinPermissionMenus() {
        List<SystemVO.MenuVO> roots = new ArrayList<>();

        SystemVO.MenuVO settingsRoot = menu(
                -1000L,
                0L,
                "settings.root",
                "系统设置",
                "CATALOG",
                "/settings",
                "@/layouts/SettingsLayout",
                "SettingOutlined",
                20,
                "system:view"
        );

        settingsRoot.setChildren(new ArrayList<>(List.of(
                menu(-1001L, -1000L, "settings.menus", "菜单管理", "MENU", "/settings/menus", "@/pages/settings/menus", "AppstoreOutlined", 1, "system:menu:view"),
                menu(-1002L, -1000L, "settings.dicts", "字典管理", "MENU", "/settings/dicts", "@/pages/settings/dicts", "DatabaseOutlined", 2, "system:dict:view"),
                menu(-1003L, -1000L, "settings.profile-fields", "字段管理", "MENU", "/settings/profile-fields", "@/pages/settings/profile-fields", "FormOutlined", 3, "system:config:view"),
                menu(-1004L, -1000L, "settings.personalization", "个性化设置", "MENU", "/settings/personalization", "@/pages/settings/personalization", "SkinOutlined", 4, "system:config:view"),
                menu(-1005L, -1000L, "settings.security", "安全设置", "MENU", "/settings/security", "@/pages/settings/security", "SafetyOutlined", 5, "system:config:view"),
                menu(-1006L, -1000L, "settings.verification", "验证管理", "MENU", "/settings/verification", "@/pages/settings/verification", "SafetyOutlined", 6, "system:verification:view"),
                menu(-1007L, -1000L, "settings.notifications", "站内信归档", "MENU", "/settings/notifications", "@/pages/settings/notifications/index", "NotificationOutlined", 7, "system:notification:view"),
                menu(-1008L, -1000L, "settings.plugins", "插件管理中心", "MENU", "/settings/plugins", "@/pages/settings/Plugins", "ApiOutlined", 8, "plugin:management:view"),
                menu(-1009L, -1000L, "settings.files", "全站文件管理", "MENU", "/settings/files/all", "@/pages/files/Center", "FolderOpenOutlined", 9, "system:file:manage"),
                monitoringRoot()
        )));

        roots.add(settingsRoot);
        return roots;
    }

    private static SystemVO.MenuVO monitoringRoot() {
        SystemVO.MenuVO monitoring = menu(
                -1010L,
                -1000L,
                "settings.monitoring",
                "系统监控",
                "MENU",
                "/settings/monitoring",
                "@/pages/settings/monitoring/index",
                "FundOutlined",
                10,
                "system:monitor:view"
        );
        monitoring.setChildren(new ArrayList<>(List.of(
                menu(-1011L, -1010L, "settings.monitoring.api-docs", "接口文档", "MENU", "/settings/monitoring/api-docs", "@/pages/settings/monitoring/ApiDocs", "FileTextOutlined", 1, "system:monitor:docs:view"),
                menu(-1012L, -1010L, "settings.monitoring.audit", "审计中心", "MENU", "/settings/monitoring/audit", "@/pages/settings/monitoring/Audit", "AuditOutlined", 2, "audit:view")
        )));
        return monitoring;
    }

    private static SystemVO.MenuVO menu(
            Long id,
            Long parentId,
            String menuCode,
            String menuName,
            String menuType,
            String path,
            String component,
            String icon,
            Integer sortNo,
            String permissionKey
    ) {
        SystemVO.MenuVO menu = new SystemVO.MenuVO();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuCode(menuCode);
        menu.setMenuName(menuName);
        menu.setMenuType(menuType);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setIcon(icon);
        menu.setSortNo(sortNo);
        menu.setPermissionKey(permissionKey);
        menu.setStatus("ENABLED");
        return menu;
    }
}
