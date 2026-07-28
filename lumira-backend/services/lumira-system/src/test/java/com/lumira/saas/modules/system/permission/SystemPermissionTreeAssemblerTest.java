package com.lumira.saas.modules.system.permission;

import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPermissionTreeAssemblerTest {

    @Test
    void shouldOnlyAttachRealAiManagementActionsToAiEmployeePage() {
        SystemPermissionTreeAssembler assembler = new SystemPermissionTreeAssembler();

        List<SystemVO.PermissionTreeVO> tree = assembler.build(
                List.of(menu("数字员工", "/ai-employees", "ai:view")),
                List.of(
                        permission("ai:view", "查看数字员工"),
                        permission("ai:employee:create", "创建数字员工"),
                        permission("ai:employee:update", "编辑数字员工"),
                        permission("ai:llm:create", "创建 LLM 服务"),
                        permission("ai:chat:send", "发送 AI 对话"),
                        permission("ai:skill:view", "查看技能列表"),
                        permission("ai:tool:view", "查看 AI 工具"),
                        permission("ai:tool:execute", "执行 AI 工具")
                )
        );

        Set<String> actionKeys = tree.getFirst().getActionPermissions().stream()
                .map(SystemVO.PermissionActionVO::getPermissionKey)
                .collect(Collectors.toSet());

        assertTrue(actionKeys.contains("ai:employee:create"));
        assertTrue(actionKeys.contains("ai:employee:update"));
        assertTrue(actionKeys.contains("ai:llm:create"));
        assertTrue(actionKeys.contains("ai:skill:view"));
        assertTrue(actionKeys.contains("ai:tool:view"));
        assertTrue(actionKeys.contains("ai:tool:execute"));
        assertFalse(actionKeys.contains("ai:chat:send"));
    }

    @Test
    void shouldSeparatePersonalAndTenantFileActions() {
        SystemPermissionTreeAssembler assembler = new SystemPermissionTreeAssembler();

        List<SystemVO.PermissionTreeVO> tree = assembler.build(
                List.of(
                        menu("我的文件", "/user-center/personal-center/files", "system:file:view"),
                        menu("全站文件管理", "/settings/files/all", "system:file:manage")
                ),
                List.of(
                        permission("system:file:view", "查看文件中心"),
                        permission("system:file:upload", "上传文档"),
                        permission("system:file:delete", "删除个人文件"),
                        permission("system:file:manage", "管理全站文件"),
                        permission("system:file:manage:delete", "删除全站文件")
                )
        );

        Set<String> personalActionKeys = tree.get(0).getActionPermissions().stream()
                .map(SystemVO.PermissionActionVO::getPermissionKey)
                .collect(Collectors.toSet());
        assertTrue(personalActionKeys.contains("system:file:upload"));
        assertTrue(personalActionKeys.contains("system:file:delete"));
        assertFalse(personalActionKeys.contains("system:file:manage"));
        assertFalse(personalActionKeys.contains("system:file:manage:delete"));
        assertFalse(tree.stream().anyMatch(node -> "/settings/files/all".equals(node.getRoutePath())));
    }

    @Test
    void shouldHideSettingsPagesFromRolePermissionTree() {
        SystemPermissionTreeAssembler assembler = new SystemPermissionTreeAssembler();

        List<SystemVO.PermissionTreeVO> tree = assembler.build(
                List.of(menu("系统设置", "/settings/menus", "system:menu:view")),
                List.of(permission("system:menu:view", "查看菜单"))
        );

        assertTrue(tree.isEmpty());
    }

    @Test
    void shouldExposePluginSettingsPagesAndActionsInRolePermissionTree() {
        SystemPermissionTreeAssembler assembler = new SystemPermissionTreeAssembler();
        SystemVO.MenuVO settingsRoot = menu("系统设置", "/settings", null);
        settingsRoot.setMenuType("CATALOG");
        settingsRoot.setChildren(List.of(
                menu("敏感词管理", "/settings/sensitive-words", "plugin:sensitive-words:view")
        ));

        List<SystemVO.PermissionTreeVO> tree = assembler.build(
                List.of(settingsRoot),
                List.of(
                        permission("plugin:sensitive-words:view", "查看敏感词"),
                        permission("plugin:sensitive-words:manage", "管理敏感词"),
                        permission("plugin:sensitive-words:import", "导入敏感词")
                )
        );

        assertTrue(tree.stream()
                .flatMap(node -> node.getChildren().stream())
                .anyMatch(node -> "/settings/sensitive-words".equals(node.getRoutePath())
                        && node.getActionPermissions().stream()
                        .map(SystemVO.PermissionActionVO::getPermissionKey)
                        .collect(Collectors.toSet())
                        .containsAll(Set.of("plugin:sensitive-words:manage", "plugin:sensitive-words:import"))));
    }

    @Test
    void shouldExposePublicFilePublishAsPersonalFileAction() {
        SystemPermissionTreeAssembler assembler = new SystemPermissionTreeAssembler();

        List<SystemVO.PermissionTreeVO> tree = assembler.build(
                List.of(menu("Files", "/user-center/personal-center/files", "system:file:view")),
                List.of(
                        permission("system:file:view", "View files"),
                        permission("system:file:upload", "Upload files"),
                        permission("system:file:publish", "Publish public files")
                )
        );

        Set<String> actionKeys = tree.getFirst().getActionPermissions().stream()
                .map(SystemVO.PermissionActionVO::getPermissionKey)
                .collect(Collectors.toSet());

        assertTrue(actionKeys.contains("system:file:publish"));
    }

    @Test
    void shouldKeepCatalogRoutePathForFrontendLocalization() {
        SystemPermissionTreeAssembler assembler = new SystemPermissionTreeAssembler();
        SystemVO.MenuVO root = menu("Expert library", "/experts", null);
        root.setMenuType("CATALOG");
        root.setChildren(List.of(menu("专家管理", "/experts/management", "expert:view")));

        List<SystemVO.PermissionTreeVO> tree = assembler.build(
                List.of(root),
                List.of(permission("expert:view", "查看专家"))
        );

        assertTrue(tree.stream().anyMatch(node -> "CATALOG".equals(node.getNodeType()) && "/experts".equals(node.getRoutePath())));
    }

    @Test
    void shouldAttachDatabaseButtonMenusAsPageActions() {
        SystemPermissionTreeAssembler assembler = new SystemPermissionTreeAssembler();
        SystemVO.MenuVO page = menu("AI 助手", "/ai/assistant", "ai:view");
        page.setChildren(List.of(button("发送消息", "ai:chat:send")));

        List<SystemVO.PermissionTreeVO> tree = assembler.build(
                List.of(page),
                List.of(
                        permission("ai:view", "访问 AI 助手"),
                        permission("ai:chat:send", "发送 AI 对话")
                )
        );

        Set<String> actionKeys = tree.getFirst().getActionPermissions().stream()
                .map(SystemVO.PermissionActionVO::getPermissionKey)
                .collect(Collectors.toSet());

        assertTrue(actionKeys.contains("ai:chat:send"));
    }

    @Test
    void shouldAttachMessageActionsToNotificationPage() {
        SystemPermissionTreeAssembler assembler = new SystemPermissionTreeAssembler();

        List<SystemVO.PermissionTreeVO> tree = assembler.build(
                List.of(menu("通知中心", "/message/center", "system:notification:view")),
                List.of(
                        permission("system:notification:view", "查看消息通知"),
                        permission("system:notification:write", "发送系统通知"),
                        permission("message:message:view", "查看站内消息"),
                        permission("message:message:read", "标记消息已读"),
                        permission("message:message:write", "发送站内消息"),
                        permission("message:message:retract", "撤回站内消息")
                )
        );

        Set<String> actionKeys = tree.getFirst().getActionPermissions().stream()
                .map(SystemVO.PermissionActionVO::getPermissionKey)
                .collect(Collectors.toSet());

        assertTrue(actionKeys.contains("system:notification:write"));
        assertTrue(actionKeys.contains("message:message:view"));
        assertTrue(actionKeys.contains("message:message:read"));
        assertTrue(actionKeys.contains("message:message:write"));
        assertTrue(actionKeys.contains("message:message:retract"));
    }

    private SystemVO.MenuVO menu(String name, String path, String permissionKey) {
        SystemVO.MenuVO menu = new SystemVO.MenuVO();
        menu.setId(1L);
        menu.setMenuName(name);
        menu.setMenuType("MENU");
        menu.setPath(path);
        menu.setPermissionKey(permissionKey);
        return menu;
    }

    private SystemVO.MenuVO button(String name, String permissionKey) {
        SystemVO.MenuVO menu = new SystemVO.MenuVO();
        menu.setId(2L);
        menu.setMenuName(name);
        menu.setMenuType("BUTTON");
        menu.setPermissionKey(permissionKey);
        return menu;
    }

    private SystemVO.PermissionVO permission(String permissionKey, String permissionName) {
        SystemVO.PermissionVO permission = new SystemVO.PermissionVO();
        permission.setPermissionKey(permissionKey);
        permission.setPermissionName(permissionName);
        permission.setPermissionGroup(permissionKey.split(":")[0]);
        permission.setSourceType("CORE");
        return permission;
    }
}
