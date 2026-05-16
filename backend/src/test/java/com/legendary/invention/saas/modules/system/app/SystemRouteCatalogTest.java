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
                        "site.pages",
                        "site.contents",
                        "site.forms",
                        "site.submissions"
                );
        assertThat(flattenedMenus)
                .extracting(SystemVO.MenuVO::getPath)
                .contains(
                        "/site",
                        "/site/settings",
                        "/site/navigation",
                        "/site/pages",
                        "/site/contents",
                        "/site/forms",
                        "/site/submissions"
                );
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site/settings")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/site/settings")).isTrue();
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
