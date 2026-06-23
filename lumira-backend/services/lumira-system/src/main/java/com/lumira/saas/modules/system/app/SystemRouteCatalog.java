package com.lumira.saas.modules.system.app;

import com.lumira.saas.modules.system.vo.SystemVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SystemRouteCatalog {

    private static final Set<String> BUILT_IN_ROUTE_PATHS = Set.of(
            "/dashboard/home",
            "/ai",
            "/ai/assistant",
            "/ai/knowledge",
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
            "/download-center",
            "/team",
            "/team/create",
            "/team/join",
            "/team/:teamId",
            "/team/:teamId/members",
            "/team/:teamId/invites",
            "/settings",
            "/settings/overview",
            "/settings/menus",
            "/settings/dicts",
            "/settings/profile-fields",
            "/settings/personalization",
            "/settings/security",
            "/settings/verification",
            "/settings/payment",
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
            "/settings/api-docs",
            "/settings/audit",
            "/settings/files",
            "/settings/files/all",
            "/user-center",
            "/user-center/users",
            "/user-center/departments",
            "/user-center/online-users",
            "/user-center/roles",
            "/user-center/personal-center",
            "/user-center/personal-center/profile",
            "/user-center/permissions",
            "/user-center/files"
    );

    private static final Set<String> BUILT_IN_COMPONENT_PATHS = Set.of(
            "@/pages/dashboard/DashboardHomePage",
            "redirect:/ai/assistant",
            "@/pages/ai/Assistant",
            "@/pages/ai/knowledge/KnowledgePage",
            "@/layouts/SettingsLayout",
            "@/pages/settings/menus",
            "@/pages/settings/dicts",
            "@/pages/settings/profile-fields",
            "@/pages/settings/personalization",
            "@/pages/settings/security",
            "@/pages/settings/verification",
            "@/pages/settings/payment",
            "@/pages/settings/notifications/index",
            "@/pages/settings/ai-employees",
            "@/pages/settings/Plugins",
            "@/pages/settings/plugins",
            "@/pages/settings/monitoring/index",
            "@/pages/settings/monitoring/ApiDocs",
            "@/pages/settings/monitoring/Audit",
            "@/pages/settings/localization",
            "@/pages/settings/files/Center",
            "@/pages/user-center/index",
            "@/pages/iam/Overview",
            "@/pages/system/users",
            "@/pages/system/departments",
            "@/pages/system/online-users",
            "@/pages/system/roles",
            "@/pages/profile/Center",
            "@/pages/files/Center",
            "@/pages/files/DownloadCenter",
            "@/pages/team",
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
        if (menu.isBuiltin() && "BUTTON".equalsIgnoreCase(menu.getMenuType())) {
            return true;
        }
        return isBuiltInMenuPath(menu.getPath()) || isBuiltInMenuComponent(menu.getComponent());
    }

    public static List<SystemVO.MenuVO> buildBuiltinPermissionMenus() {
        List<SystemVO.MenuVO> roots = new ArrayList<>();

        SystemVO.MenuVO aiRoot = menu(
                -990L,
                0L,
                "ai.root",
                "AI",
                "CATALOG",
                "/ai",
                "redirect:/ai/assistant",
                "RobotOutlined",
                2,
                null
        );
        aiRoot.setChildren(new ArrayList<>(List.of(
                menu(-989L, -990L, "ai.assistant", "AI 助手", "MENU", "/ai/assistant", "@/pages/ai/Assistant", "RobotOutlined", 1, "ai:chat:send"),
                menu(-988L, -990L, "ai.knowledge", "知识库", "MENU", "/ai/knowledge", "@/pages/ai/knowledge/KnowledgePage", "FileSearchOutlined", 2, "ai:knowledge:view")
        )));

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

        SystemVO.MenuVO settingsMenus = menu(-1001L, -1000L, "settings.menus", "菜单管理", "MENU", "/settings/menus", "@/pages/settings/menus", "AppstoreOutlined", 2, "system:menu:view");
        settingsMenus.setChildren(new ArrayList<>(List.of(
                menu(-1020L, -1001L, "settings.menus.create", "创建菜单", "BUTTON", null, null, null, 1, "system:menu:create"),
                menu(-1021L, -1001L, "settings.menus.update", "编辑菜单", "BUTTON", null, null, null, 2, "system:menu:update"),
                menu(-1022L, -1001L, "settings.menus.delete", "删除菜单", "BUTTON", null, null, null, 3, "system:menu:delete")
        )));
        SystemVO.MenuVO settingsDicts = menu(-1002L, -1000L, "settings.dicts", "字典管理", "MENU", "/settings/dicts", "@/pages/settings/dicts", "DatabaseOutlined", 3, "system:dict:view");
        settingsDicts.setChildren(new ArrayList<>(List.of(
                menu(-1023L, -1002L, "settings.dicts.create", "创建字典", "BUTTON", null, null, null, 1, "system:dict:create"),
                menu(-1024L, -1002L, "settings.dicts.update", "编辑字典", "BUTTON", null, null, null, 2, "system:dict:update"),
                menu(-1025L, -1002L, "settings.dicts.delete", "删除字典", "BUTTON", null, null, null, 3, "system:dict:delete")
        )));

        settingsRoot.setChildren(new ArrayList<>(List.of(
                settingsMenus,
                settingsDicts,
                menu(-1003L, -1000L, "settings.profile-fields", "字段管理", "MENU", "/settings/profile-fields", "@/pages/settings/profile-fields", "FormOutlined", 4, "system:config:view"),
                menu(-1004L, -1000L, "settings.personalization", "个性化设置", "MENU", "/settings/personalization", "@/pages/settings/personalization", "SkinOutlined", 5, "system:config:view"),
                menu(-1005L, -1000L, "settings.security", "安全设置", "MENU", "/settings/security", "@/pages/settings/security", "SafetyOutlined", 6, "system:config:view"),
                menu(-1006L, -1000L, "settings.verification", "验证管理", "MENU", "/settings/verification", "@/pages/settings/verification", "SafetyOutlined", 7, "system:verification:view"),
                menu(-1007L, -1000L, "settings.payment", "支付设置", "MENU", "/settings/payment", "@/pages/settings/payment", "CreditCardOutlined", 8, "payment:view"),
                menu(-1008L, -1000L, "settings.notifications", "通知中心", "MENU", "/settings/notifications", "@/pages/settings/notifications/index", "NotificationOutlined", 9, "system:notification:view"),
                menu(-1009L, -1000L, "settings.plugins", "插件管理中心", "MENU", "/settings/plugins", "@/pages/settings/plugins", "ApiOutlined", 10, "plugin:management:view"),
                menu(-1010L, -1000L, "settings.ai-employees", "数字员工", "MENU", "/settings/ai-employees", "@/pages/settings/ai-employees", "RobotOutlined", 24, "ai:view"),
                menu(-1011L, -1000L, "localization.root", "本地化中心", "MENU", "/settings/localization", "@/pages/settings/localization", "TranslationOutlined", 29, "localization:view"),
                menu(-1012L, -1000L, "settings.files", "全站文件管理", "MENU", "/settings/files/all", "@/pages/settings/files/Center", "FolderOpenOutlined", 9, "system:file:manage"),
                monitoringRoot(),
                menu(-1013L, -1000L, "settings.monitoring.api-docs", "接口文档", "MENU", "/settings/api-docs", "@/pages/settings/monitoring/ApiDocs", "FileTextOutlined", 11, "system:monitor:docs:view"),
                menu(-1014L, -1000L, "settings.monitoring.audit", "审计中心", "MENU", "/settings/audit", "@/pages/settings/monitoring/Audit", "AuditOutlined", 12, "audit:view")
        )));

        roots.add(menu(-955L, 0L, "dashboard.home", "首页", "MENU", "/dashboard/home", "@/pages/dashboard/DashboardHomePage", "DashboardOutlined", 0, "dashboard:view"));
        roots.add(menu(-956L, 0L, "files.download-center", "下载中心", "MENU", "/download-center", "@/pages/files/DownloadCenter", "DownloadOutlined", 1, "download:center:view"));
        roots.add(aiRoot);
        SystemVO.MenuVO teamRoot = menu(-957L, 0L, "team.root", "团队", "MENU", "/team", "@/pages/team", "TeamOutlined", 3, "team:view");
        teamRoot.setChildren(new ArrayList<>(List.of(
                menu(-958L, -957L, "team.create", "创建团队", "BUTTON", null, null, null, 1, "team:create"),
                menu(-959L, -957L, "team.update", "编辑团队", "BUTTON", null, null, null, 2, "team:update"),
                menu(-960L, -957L, "team.delete", "删除团队", "BUTTON", null, null, null, 3, "team:delete"),
                menu(-961L, -957L, "team.member.view", "查看成员", "BUTTON", null, null, null, 4, "team:member:view"),
                menu(-962L, -957L, "team.member.invite", "邀请成员", "BUTTON", null, null, null, 5, "team:member:invite"),
                menu(-963L, -957L, "team.member.remove", "移除成员", "BUTTON", null, null, null, 6, "team:member:remove"),
                menu(-964L, -957L, "team.member.role-update", "更新成员角色", "BUTTON", null, null, null, 7, "team:member:role-update")
        )));
        roots.add(teamRoot);
        SystemVO.MenuVO userCenterRoot = menu(-950L, 0L, "user.center.root", "用户中心", "CATALOG", "/user-center", "@/layouts/SettingsLayout", "TeamOutlined", 18, "user:center:view");
        SystemVO.MenuVO systemUsers = menu(-951L, -950L, "system.users", "用户管理", "MENU", "/user-center/users", "@/pages/system/users", "TeamOutlined", 21, "system:user:view");
        systemUsers.setChildren(new ArrayList<>(List.of(
                menu(-965L, -951L, "system.users.create", "创建用户", "BUTTON", null, null, null, 1, "system:user:create"),
                menu(-966L, -951L, "system.users.update", "编辑用户", "BUTTON", null, null, null, 2, "system:user:update"),
                menu(-967L, -951L, "system.users.delete", "删除用户", "BUTTON", null, null, null, 3, "system:user:delete"),
                menu(-968L, -951L, "system.users.export", "导出用户", "BUTTON", null, null, null, 4, "system:user:export")
        )));
        SystemVO.MenuVO systemRoles = menu(-953L, -950L, "system.roles", "角色管理", "MENU", "/user-center/roles", "@/pages/system/roles", "SafetyOutlined", 24, "system:role:view");
        systemRoles.setChildren(new ArrayList<>(List.of(
                menu(-969L, -953L, "system.roles.create", "创建角色", "BUTTON", null, null, null, 1, "system:role:create"),
                menu(-970L, -953L, "system.roles.update", "编辑角色", "BUTTON", null, null, null, 2, "system:role:update"),
                menu(-971L, -953L, "system.roles.delete", "删除角色", "BUTTON", null, null, null, 3, "system:role:delete"),
                menu(-972L, -953L, "system.roles.grant", "授权角色", "BUTTON", null, null, null, 4, "system:role:grant")
        )));
        userCenterRoot.setChildren(new ArrayList<>(List.of(
                systemUsers,
                menu(-954L, -950L, "system.departments", "组织部门", "MENU", "/user-center/departments", "@/pages/system/departments", "ApartmentOutlined", 22, "system:department:view"),
                menu(-952L, -950L, "system.online-users", "在线用户", "MENU", "/user-center/online-users", "@/pages/system/online-users", "UserSwitchOutlined", 23, "system:online-user:view"),
                systemRoles
        )));
        SystemVO.MenuVO personalCenterRoot = menu(-940L, 0L, "user.center.personal", "个人中心", "CATALOG", "/user-center/personal-center", "@/layouts/SettingsLayout", "IdcardOutlined", 19, "profile:view");
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
                -1015L,
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
        menu.setBuiltin(true);
        return menu;
    }
}
