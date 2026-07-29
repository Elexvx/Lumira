package com.lumira.saas.modules.system.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.activity.app.ActivityManagementAppService;
import com.lumira.saas.modules.activity.app.ActivityRegistrationAppService;
import com.lumira.saas.modules.activity.controller.ActivityRegistrationV2Controller;
import com.lumira.saas.modules.activity.controller.ActivityV2Controller;
import com.lumira.saas.modules.activity.controller.PublicActivityController;
import com.lumira.saas.modules.activity.infrastructure.JdbcActivityRepository;
import com.lumira.saas.modules.activity.infrastructure.persistence.JdbcActivityRegistrationRepository;
import com.lumira.saas.modules.architecture.application.OwnerReadModelMetricsService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.architecture.interfaces.rest.DddArchitectureCatalogController;
import com.lumira.saas.modules.competition.app.CertificateAppService;
import com.lumira.saas.modules.competition.app.CertificateRenderService;
import com.lumira.saas.modules.competition.app.CompetitionManagementAppService;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationAppService;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportAppService;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportTaskWorkerService;
import com.lumira.saas.modules.competition.controller.CertificateV2Controller;
import com.lumira.saas.modules.competition.controller.CompetitionRegistrationExportController;
import com.lumira.saas.modules.competition.controller.CompetitionRegistrationV2Controller;
import com.lumira.saas.modules.competition.controller.CompetitionV2Controller;
import com.lumira.saas.modules.competition.event.CompetitionPaymentEventHandler;
import com.lumira.saas.modules.competition.infrastructure.JdbcCertificateRecordRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcCertificateTemplateRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcRegistrationDatasetRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcRegistrationExportTaskRepository;
import com.lumira.saas.modules.competition.infrastructure.RegistrationReviewInternalApiAdapter;
import com.lumira.saas.modules.config.controller.HealthController;
import com.lumira.saas.modules.config.controller.VersionController;
import com.lumira.saas.modules.config.runtime.DatabaseVersionStartupRecorder;
import com.lumira.saas.modules.expert.app.ExpertManagementAppService;
import com.lumira.saas.modules.expert.app.ExpertApprovalEventConsumer;
import com.lumira.saas.modules.expert.controller.ExpertV2Controller;
import com.lumira.saas.modules.expert.infrastructure.JdbcExpertApprovalRepository;
import com.lumira.saas.modules.expert.infrastructure.JdbcExpertRepository;
import com.lumira.saas.modules.draft.app.UserDraftAppService;
import com.lumira.saas.modules.draft.controller.UserDraftController;
import com.lumira.saas.modules.draft.infrastructure.JdbcUserDraftRepository;
import com.lumira.saas.modules.project.app.ProjectManagementAppService;
import com.lumira.saas.modules.project.controller.ProjectV2Controller;
import com.lumira.saas.modules.project.infrastructure.JdbcProjectRepository;
import com.lumira.saas.modules.review.app.ReviewAppService;
import com.lumira.saas.modules.review.controller.ReviewV2Controller;
import com.lumira.saas.modules.review.infrastructure.JdbcReviewRepository;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import com.lumira.saas.modules.workflow.controller.WorkflowV2Controller;
import com.lumira.saas.infrastructure.job.InternalRegistrationExportJobController;
import com.lumira.saas.infrastructure.job.InternalReviewJobController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        ActivityManagementAppService.class,
        ActivityRegistrationAppService.class,
        ActivityRegistrationV2Controller.class,
        ActivityV2Controller.class,
        JdbcActivityRepository.class,
        JdbcActivityRegistrationRepository.class,
        PublicActivityController.class,
        CertificateAppService.class,
        CertificateRenderService.class,
        CompetitionManagementAppService.class,
        CompetitionRegistrationAppService.class,
        CompetitionRegistrationExportAppService.class,
        CompetitionRegistrationExportTaskWorkerService.class,
        CertificateV2Controller.class,
        CompetitionRegistrationExportController.class,
        CompetitionRegistrationV2Controller.class,
        CompetitionV2Controller.class,
        CompetitionPaymentEventHandler.class,
        JdbcCertificateRecordRepository.class,
        JdbcCertificateTemplateRepository.class,
        JdbcRegistrationDatasetRepository.class,
        JdbcRegistrationExportTaskRepository.class,
        RegistrationReviewInternalApiAdapter.class,
        InternalRegistrationExportJobController.class,
        InternalReviewJobController.class,
        DatabaseVersionStartupRecorder.class,
        DddArchitectureCatalogController.class,
        JdbcUserDraftRepository.class,
        UserDraftAppService.class,
        UserDraftController.class,
        ExpertApprovalEventConsumer.class,
        ExpertManagementAppService.class,
        ExpertV2Controller.class,
        JdbcExpertApprovalRepository.class,
        JdbcExpertRepository.class,
        HealthController.class,
        OwnerReadModelMetricsService.class,
        OwnerRuntimeMetrics.class,
        ProjectManagementAppService.class,
        ProjectV2Controller.class,
        JdbcProjectRepository.class,
        ReviewAppService.class,
        ReviewV2Controller.class,
        JdbcReviewRepository.class,
        WorkflowAppService.class,
        WorkflowV2Controller.class,
        VersionController.class
})
public class SystemBusinessControlPlaneAssemblyConfiguration {
}
