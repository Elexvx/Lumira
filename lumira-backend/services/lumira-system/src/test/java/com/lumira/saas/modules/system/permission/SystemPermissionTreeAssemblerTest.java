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
                        menu("我的文件", "/user-center/files", "system:file:view"),
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

    private SystemVO.MenuVO menu(String name, String path, String permissionKey) {
        SystemVO.MenuVO menu = new SystemVO.MenuVO();
        menu.setId(1L);
        menu.setMenuName(name);
        menu.setMenuType("MENU");
        menu.setPath(path);
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
