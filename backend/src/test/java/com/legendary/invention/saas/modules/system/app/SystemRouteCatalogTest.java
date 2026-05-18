package com.legendary.invention.saas.modules.system.app;

import com.legendary.invention.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRouteCatalogTest {

    @Test
    void builtinPermissionMenus_shouldContainSiteManagementRoutes() {
        List<SystemVO.MenuVO> flattenedMenus = flatten(SystemRouteCatalog.buildBuiltinPermissionMenus());

        assertThat(flattenedMenus)
                .extracting(SystemVO.MenuVO::getMenuCode)
                .contains(
                        "site.root",
                        "site.settings",
                        "site.navigation",
                        "site.carousels",
                        "site.pages",
                        "site.contents",
                        "site.forms",
                        "site.submissions",
                        "dashboard.home",
                        "user.center.personal"
                );
        assertThat(flattenedMenus)
                .extracting(SystemVO.MenuVO::getPath)
                .contains(
                        "/dashboard/home",
                        "/site",
                        "/site/settings",
                        "/site/navigation",
                        "/site/carousels",
                        "/site/pages",
                        "/site/contents",
                        "/site/forms",
                        "/site/submissions",
                        "/user-center/personal-center"
                );
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
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/dashboard/home")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site/settings")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site/carousels")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/user-center/permissions")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/dashboard/Home")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/site/settings")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/site/carousels")).isTrue();
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
