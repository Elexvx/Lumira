package com.lumira.saas.modules.system.app;

import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRouteCatalogTest {

    @Test
    void builtinPermissionMenus_shouldContainCoreRoutes() {
        List<SystemVO.MenuVO> flattenedMenus = flatten(SystemRouteCatalog.buildBuiltinPermissionMenus());

        assertThat(flattenedMenus)
                .extracting(SystemVO.MenuVO::getMenuCode)
                .contains(
                        "dashboard.home",
                        "ai.root",
                        "settings.root",
                        "user.center.personal"
                )
                .doesNotContain("site.root");
        assertThat(flattenedMenus)
                .extracting(SystemVO.MenuVO::getPath)
                .contains(
                        "/dashboard/home",
                        "/ai",
                        "/ai/assistant",
                        "/ai/knowledge",
                        "/settings",
                        "/user-center/users",
                        "/user-center/roles",
                        "/user-center/personal-center"
                )
                .doesNotContain("/site", "/site/settings", "/site/carousels");
        assertThat(flattenedMenus)
                .filteredOn(menu -> "dashboard.home".equals(menu.getMenuCode()))
                .singleElement()
                .satisfies(menu -> assertThat(menu.getComponent()).isEqualTo("@/pages/dashboard/DashboardHomePage"));
        assertThat(flattenedMenus)
                .filteredOn(menu -> "ai.knowledge".equals(menu.getMenuCode()))
                .singleElement()
                .satisfies(menu -> assertThat(menu.getComponent()).isEqualTo("@/pages/ai/knowledge/KnowledgePage"));
        assertThat(flattenedMenus)
                .filteredOn(menu -> "user.center.personal".equals(menu.getMenuCode()))
                .singleElement()
                .satisfies(menu -> {
                    assertThat(menu.getPath()).isEqualTo("/user-center/personal-center");
                    assertThat(menu.getComponent()).isEqualTo("@/layouts/SettingsLayout");
                });
        assertThat(flattenedMenus)
                .filteredOn(menu -> menu.getPath() != null)
                .allSatisfy(menu -> assertThat(SystemRouteCatalog.isBuiltInMenuPath(menu.getPath())).isTrue());
        assertThat(flattenedMenus)
                .filteredOn(menu -> menu.getComponent() != null)
                .allSatisfy(menu -> assertThat(SystemRouteCatalog.isBuiltInMenuComponent(menu.getComponent())).isTrue());
        assertThat(flattenedMenus)
                .allSatisfy(menu -> {
                    assertThat(menu.isBuiltin()).isTrue();
                    assertThat(SystemRouteCatalog.isBuiltInMenu(menu)).isTrue();
                });
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/dashboard/home")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site/settings")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site/carousels")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/user-center/permissions")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/dashboard/DashboardHomePage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/ai/knowledge/KnowledgePage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/dashboard/Home")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/site/settings")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/site/carousels")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/iam/Overview")).isTrue();
    }

    private static List<SystemVO.MenuVO> flatten(List<SystemVO.MenuVO> menus) {
        List<SystemVO.MenuVO> result = new ArrayList<>();
        for (SystemVO.MenuVO menu : menus) {
            result.add(menu);
            if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
                result.addAll(flatten(menu.getChildren()));
            }
        }
        return result;
    }
}
