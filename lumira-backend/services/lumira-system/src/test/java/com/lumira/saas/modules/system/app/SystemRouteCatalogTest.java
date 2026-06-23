package com.lumira.saas.modules.system.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRouteCatalogTest {

    @Test
    void routeCatalogShouldAllowSeededSystemMenuRoutes() {
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/dashboard/home")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/activities")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/activities/management")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/activities/search")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/competitions")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/competitions/management")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/experts")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/experts/management")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/aiadc")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/aiadc/activity-management")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/aiadc/activities")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/aiadc/activity-search")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/aiadc/project-management")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/aiadc/projects")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/aiadc/project-search")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/team")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/team/management")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/team/search")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/team/:teamId/members")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site/settings")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site/carousels")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/user-center/permissions")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/user-center/personal-center/files")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/user-center/files")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/dashboard/DashboardHomePage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/ai/knowledge/KnowledgePage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/activity")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/competition")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/expert")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/project")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/team")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/dashboard/Home")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/site/settings")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/site/carousels")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/iam/Overview")).isTrue();
    }
}
