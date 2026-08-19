package com.lumira.saas.modules.competition.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.competition.app.CertificateAppService;
import com.lumira.saas.modules.competition.app.CertificateRenderService;
import com.lumira.saas.modules.competition.app.CompetitionManagementAppService;
import com.lumira.saas.modules.competition.app.CompetitionPaymentConsistencyService;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationAppService;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportAppService;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportTaskWorkerService;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAccessPolicy;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAppService;
import com.lumira.saas.modules.competition.controller.CertificateV2Controller;
import com.lumira.saas.modules.competition.controller.CompetitionRegistrationV2Controller;
import com.lumira.saas.modules.competition.controller.CompetitionPaymentConsistencyController;
import com.lumira.saas.modules.competition.controller.CompetitionV2Controller;
import com.lumira.saas.modules.competition.controller.CompetitionWorkspaceAuditController;
import com.lumira.saas.modules.competition.controller.CompetitionWorkspaceCertificateController;
import com.lumira.saas.modules.competition.controller.CompetitionWorkspaceController;
import com.lumira.saas.modules.competition.controller.CompetitionWorkspaceExportController;
import com.lumira.saas.modules.competition.controller.CompetitionWorkspaceRegistrationController;
import com.lumira.saas.modules.competition.controller.internal.InternalRegistrationExportJobController;
import com.lumira.saas.modules.competition.controller.internal.InternalReviewJobController;
import com.lumira.saas.modules.competition.controller.internal.CompetitionPaymentEventInternalController;
import com.lumira.saas.modules.competition.event.CompetitionPaymentEventHandler;
import com.lumira.saas.modules.competition.export.CompetitionExcelExportService;
import com.lumira.saas.modules.competition.infrastructure.CompetitionManagementPersistenceAssemblyConfiguration;
import com.lumira.saas.modules.competition.infrastructure.CompetitionRegistrationPersistenceAssemblyConfiguration;
import com.lumira.saas.modules.competition.integration.CompetitionCatalogSourceSnapshotAdapter;
import com.lumira.saas.modules.competition.infrastructure.JdbcCertificateRecordRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcCertificateTemplateRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcCompetitionAuditRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcRegistrationDatasetRepository;
import com.lumira.saas.modules.competition.infrastructure.RegistrationReviewInternalApiAdapter;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.review.app.ReviewAppService;
import com.lumira.saas.modules.review.controller.CompetitionWorkspaceReviewController;
import com.lumira.saas.modules.review.controller.ReviewV2Controller;
import com.lumira.saas.modules.review.infrastructure.JdbcReviewRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Explicit Competition ownership assembly for the modular-monolith server.
 * There is no physical Competition runtime: lumira-server imports this context.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        CompetitionSqlOperations.class,
        CompetitionManagementPersistenceAssemblyConfiguration.class,
        CompetitionRegistrationPersistenceAssemblyConfiguration.class,
        CompetitionCatalogSourceSnapshotAdapter.class,
        JdbcCertificateRecordRepository.class,
        JdbcCertificateTemplateRepository.class,
        JdbcCompetitionAuditRepository.class,
        JdbcRegistrationDatasetRepository.class,
        JdbcReviewRepository.class,
        RegistrationReviewInternalApiAdapter.class,
        CertificateRenderService.class,
        CompetitionExcelExportService.class,
        CertificateAppService.class,
        CompetitionManagementAppService.class,
        CompetitionPaymentConsistencyService.class,
        CompetitionRegistrationAppService.class,
        CompetitionRegistrationExportAppService.class,
        CompetitionRegistrationExportTaskWorkerService.class,
        CompetitionWorkspaceAccessPolicy.class,
        CompetitionWorkspaceAppService.class,
        ReviewAppService.class,
        CompetitionPaymentEventHandler.class,
        CertificateV2Controller.class,
        CompetitionRegistrationV2Controller.class,
        CompetitionPaymentConsistencyController.class,
        CompetitionV2Controller.class,
        CompetitionWorkspaceAuditController.class,
        CompetitionWorkspaceCertificateController.class,
        CompetitionWorkspaceController.class,
        CompetitionWorkspaceExportController.class,
        CompetitionWorkspaceRegistrationController.class,
        CompetitionWorkspaceReviewController.class,
        ReviewV2Controller.class,
        InternalRegistrationExportJobController.class,
        InternalReviewJobController.class,
        CompetitionPaymentEventInternalController.class
})
public class CompetitionControlPlaneAssemblyConfiguration {
}
