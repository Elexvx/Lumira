package com.legendary.invention.saas.modules.system.app;

import com.legendary.invention.saas.modules.system.vo.SystemVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SystemRouteCatalog {

    private static final Set<String> BUILT_IN_ROUTE_PATHS = Set.of(
            "/ai",
            "/tasks",
            "/approvals",
            "/evaluations",
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
            "/settings/ai-employees",
            "/settings/plugins",
            "/settings/localization",
            "/settings/monitoring",
            "/settings/monitoring/service",
            "/settings/monitoring/redis",
            "/settings/monitoring/api-docs",
            "/settings/monitoring/audit",
            "/settings/files",
            "/settings/files/all",
            "/user-center",
            "/user-center/users",
            "/user-center/online-users",
            "/user-center/roles",
            "/user-center/personal-center",
            "/user-center/personal-center/profile",
            "/user-center/files"
    );

    private static final Set<String> BUILT_IN_COMPONENT_PATHS = Set.of(
            "@/pages/ai/Assistant",
            "@/pages/tasks",
            "@/pages/approvals",
            "@/pages/evaluations",
            "@/pages/settings/menus",
            "@/pages/settings/dicts",
            "@/pages/settings/profile-fields",
            "@/pages/settings/personalization",
            "@/pages/settings/security",
            "@/pages/settings/verification",
            "@/pages/settings/notifications/index",
            "@/pages/settings/ai-employees",
            "@/pages/settings/Plugins",
            "@/pages/settings/plugins",
            "@/pages/settings/monitoring/index",
            "@/pages/settings/monitoring/ApiDocs",
            "@/pages/settings/monitoring/Audit",
            "@/pages/settings/localization",
            "@/pages/settings/files/Center",
            "@/pages/system/users",
            "@/pages/system/online-users",
            "@/pages/system/roles",
            "@/pages/profile/Center",
            "@/pages/files/Center",
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

        SystemVO.MenuVO aiRoot = menu(
                -990L,
                0L,
                "ai.assistant",
                "AI 助手",
                "MENU",
                "/ai",
                "@/pages/ai/Assistant",
                "RobotOutlined",
                2,
                "ai:chat:send"
        );

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
                menu(-1008L, -1000L, "settings.plugins", "插件管理中心", "MENU", "/settings/plugins", "@/pages/settings/plugins", "ApiOutlined", 8, "plugin:management:view"),
                menu(-1009L, -1000L, "settings.ai-employees", "数字员工", "MENU", "/settings/ai-employees", "@/pages/settings/ai-employees", "RobotOutlined", 24, "ai:view"),
                menu(-1010L, -1000L, "localization.root", "本地化中心", "MENU", "/settings/localization", "@/pages/settings/localization", "TranslationOutlined", 29, "localization:view"),
                menu(-1011L, -1000L, "settings.files", "全站文件管理", "MENU", "/settings/files/all", "@/pages/settings/files/Center", "FolderOpenOutlined", 9, "system:file:manage"),
                monitoringRoot(),
                menu(-1013L, -1000L, "settings.monitoring.api-docs", "接口文档", "MENU", "/settings/api-docs", "@/pages/settings/monitoring/ApiDocs", "FileTextOutlined", 11, "system:monitor:docs:view"),
                menu(-1014L, -1000L, "settings.monitoring.audit", "审计中心", "MENU", "/settings/audit", "@/pages/settings/monitoring/Audit", "AuditOutlined", 12, "audit:view")
        )));

        roots.add(aiRoot);
        roots.add(menu(-980L, 0L, "tasks.root", "任务中心", "MENU", "/tasks", "@/pages/tasks", "CheckSquareOutlined", 4, "task:view"));
        roots.add(menu(-970L, 0L, "approvals.root", "审批中心", "MENU", "/approvals", "@/pages/approvals", "AuditOutlined", 5, "approval:view"));
        roots.add(menu(-960L, 0L, "evaluations.root", "评审中心", "MENU", "/evaluations", "@/pages/evaluations", "StarOutlined", 6, "evaluation:view"));
        SystemVO.MenuVO userCenterRoot = menu(-950L, 0L, "user.center.root", "用户中心", "CATALOG", "/user-center", "@/layouts/SettingsLayout", "TeamOutlined", 18, "user:center:view");
        userCenterRoot.setChildren(new ArrayList<>(List.of(
                menu(-951L, -950L, "system.users", "用户管理", "MENU", "/user-center/users", "@/pages/system/users", "TeamOutlined", 21, "system:user:view"),
                menu(-952L, -950L, "system.online-users", "在线用户", "MENU", "/user-center/online-users", "@/pages/system/online-users", "UserSwitchOutlined", 22, "system:online-user:view"),
                menu(-953L, -950L, "system.roles", "角色管理", "MENU", "/user-center/roles", "@/pages/system/roles", "SafetyOutlined", 23, "system:role:view")
        )));
        SystemVO.MenuVO personalCenterRoot = menu(-940L, 0L, "user.center.personal", "个人中心", "CATALOG", "/user-center/personal-center", "redirect:/user-center/personal-center/profile", "IdcardOutlined", 19, "profile:view");
        personalCenterRoot.setChildren(new ArrayList<>(List.of(
                menu(-941L, -940L, "profile.center", "个人资料", "MENU", "/user-center/personal-center/profile", "@/pages/profile/Center", "UserOutlined", 1, "profile:view"),
                menu(-942L, -940L, "files.my", "我的文件", "MENU", "/user-center/files", "@/pages/files/Center", "FileOutlined", 2, "system:file:view")
        )));
        roots.add(userCenterRoot);
        roots.add(personalCenterRoot);
        roots.add(settingsRoot);
        return roots;
    }

    private static SystemVO.MenuVO monitoringRoot() {
        SystemVO.MenuVO monitoring = menu(
                -1012L,
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
