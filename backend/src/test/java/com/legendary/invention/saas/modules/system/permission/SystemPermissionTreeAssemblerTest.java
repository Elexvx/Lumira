package com.legendary.invention.saas.modules.system.permission;

import com.legendary.invention.saas.modules.system.vo.SystemVO;
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
                        permission("ai:skill:view", "查看技能列表")
                )
        );

        Set<String> actionKeys = tree.getFirst().getActionPermissions().stream()
                .map(SystemVO.PermissionActionVO::getPermissionKey)
                .collect(Collectors.toSet());

        assertTrue(actionKeys.contains("ai:employee:create"));
        assertTrue(actionKeys.contains("ai:employee:update"));
        assertTrue(actionKeys.contains("ai:llm:create"));
        assertFalse(actionKeys.contains("ai:chat:send"));
        assertFalse(actionKeys.contains("ai:skill:view"));
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
