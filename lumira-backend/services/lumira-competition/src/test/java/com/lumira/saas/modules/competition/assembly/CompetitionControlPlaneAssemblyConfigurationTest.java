package com.lumira.saas.modules.competition.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportAppService;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportTaskWorkerService;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAccessPolicy;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAppService;
import com.lumira.saas.modules.competition.controller.CompetitionRegistrationV2Controller;
import com.lumira.saas.modules.competition.controller.CompetitionWorkspaceAuditController;
import com.lumira.saas.modules.competition.controller.CompetitionWorkspaceCertificateController;
import com.lumira.saas.modules.competition.controller.CompetitionWorkspaceController;
import com.lumira.saas.modules.competition.controller.CompetitionWorkspaceExportController;
import com.lumira.saas.modules.competition.controller.CompetitionWorkspaceRegistrationController;
import com.lumira.saas.modules.competition.controller.internal.InternalRegistrationExportJobController;
import com.lumira.saas.modules.competition.controller.internal.InternalReviewJobController;
import com.lumira.saas.modules.competition.infrastructure.JdbcCompetitionAuditRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcRegistrationDatasetRepository;
import com.lumira.saas.modules.competition.infrastructure.RegistrationReviewInternalApiAdapter;
import com.lumira.saas.modules.competition.integration.CompetitionCatalogSourceSnapshotAdapter;
import com.lumira.saas.modules.review.app.ReviewAppService;
import com.lumira.saas.modules.review.controller.CompetitionWorkspaceReviewController;
import com.lumira.saas.modules.review.controller.ReviewV2Controller;
import com.lumira.saas.modules.review.infrastructure.JdbcReviewRepository;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;

class CompetitionControlPlaneAssemblyConfigurationTest {

    @Test
    void explicitlyImportsRegistrationDatasetExportAndReviewComponents() {
        Import imported = CompetitionControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(Arrays.asList(imported.value())).contains(
                JdbcRegistrationDatasetRepository.class,
                CompetitionCatalogSourceSnapshotAdapter.class,
                RegistrationReviewInternalApiAdapter.class,
                CompetitionRegistrationExportAppService.class,
                CompetitionRegistrationExportTaskWorkerService.class,
                InternalRegistrationExportJobController.class,
                CompetitionWorkspaceAccessPolicy.class,
                CompetitionWorkspaceAppService.class,
                CompetitionWorkspaceController.class,
                CompetitionWorkspaceRegistrationController.class,
                CompetitionWorkspaceCertificateController.class,
                CompetitionWorkspaceExportController.class,
                CompetitionWorkspaceAuditController.class,
                CompetitionWorkspaceReviewController.class,
                JdbcCompetitionAuditRepository.class,
                JdbcReviewRepository.class,
                ReviewAppService.class,
                ReviewV2Controller.class,
                InternalReviewJobController.class
        );
    }

    @Test
    void uuidWorkspaceStageRouteDoesNotCollideWithLegacyNumericStageRoute() throws NoSuchMethodException {
        GetMapping workspaceMapping = CompetitionWorkspaceRegistrationController.class
                .getDeclaredMethod("stages", String.class)
                .getAnnotation(GetMapping.class);
        GetMapping legacyMapping = CompetitionRegistrationV2Controller.class
                .getDeclaredMethod("stages", Long.class)
                .getAnnotation(GetMapping.class);

        assertThat(workspaceMapping.value()).containsExactly("/workspace/stages");
        assertThat(legacyMapping.value()).containsExactly("/competitions/{competitionId}/stages");
    }
}
