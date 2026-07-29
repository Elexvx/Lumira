package com.lumira.saas.modules.system.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.infrastructure.job.InternalRegistrationExportJobController;
import com.lumira.saas.infrastructure.job.InternalReviewJobController;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportAppService;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportTaskWorkerService;
import com.lumira.saas.modules.competition.controller.CompetitionRegistrationExportController;
import com.lumira.saas.modules.competition.infrastructure.JdbcRegistrationDatasetRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcRegistrationExportTaskRepository;
import com.lumira.saas.modules.competition.infrastructure.RegistrationReviewInternalApiAdapter;
import com.lumira.saas.modules.review.app.ReviewAppService;
import com.lumira.saas.modules.review.controller.ReviewV2Controller;
import com.lumira.saas.modules.review.infrastructure.JdbcReviewRepository;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

class SystemBusinessControlPlaneAssemblyConfigurationTest {

    @Test
    void explicitlyImportsRegistrationDatasetExportAndReviewComponents() {
        Import imported = SystemBusinessControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(Arrays.asList(imported.value())).contains(
                JdbcRegistrationDatasetRepository.class,
                JdbcRegistrationExportTaskRepository.class,
                RegistrationReviewInternalApiAdapter.class,
                CompetitionRegistrationExportAppService.class,
                CompetitionRegistrationExportTaskWorkerService.class,
                CompetitionRegistrationExportController.class,
                InternalRegistrationExportJobController.class,
                JdbcReviewRepository.class,
                ReviewAppService.class,
                ReviewV2Controller.class,
                InternalReviewJobController.class
        );
    }
}
