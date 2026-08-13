package com.lumira.saas.modules.system.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRouteCatalogTest {

    @Test
    void routeCatalogShouldAllowSeededSystemMenuRoutes() {
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/dashboard/home")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/data-management")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/data-management/download-center")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/data-management/query-center")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/activities")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/activities/management")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/activities/search")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/competitions")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/competitions/management")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/competitions/registrations")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/competitions/review-results")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/expert-review/reviews")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/competitions/create")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/competitions/expert-apply")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/registration")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/projects")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/projects/management")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/projects/search")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/payments")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/payments/management")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/payments/status")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/certificates")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/certificates/mine")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/experts")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/experts/management")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/experts/query")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/workflows")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/workflows/tasks")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/workflows/config")).isTrue();
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
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/download-center")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site/settings")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/site/carousels")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/ai")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/ai/assistant")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/user-center/permissions")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/user-center/personal-center/files")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuPath("/user-center/files")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/dashboard/DashboardHomePage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/DataManagementLandingPage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/ai/knowledge/KnowledgePage")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/activity")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/competition")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/competition/CompetitionRegistrationDataPage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/competition/CompetitionReviewResultsPage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/competition/CompetitionReviewPage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/certificates/MyCertificatesPage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/workflow/WorkflowTasksPage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/workflow/WorkflowConfigPage")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/expert")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/project")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/payment")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/team")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/dashboard/Home")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/site/settings")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/site/carousels")).isFalse();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("@/pages/iam/Overview")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("redirect:/dashboard/home")).isTrue();
        assertThat(SystemRouteCatalog.isBuiltInMenuComponent("redirect:/team/search")).isFalse();
    }
}
