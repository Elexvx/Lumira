package com.lumira.saas.modules.system.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.activity.app.ActivityManagementAppService;
import com.lumira.saas.modules.activity.controller.ActivityV2Controller;
import com.lumira.saas.modules.activity.controller.PublicActivityController;
import com.lumira.saas.modules.architecture.application.OwnerReadModelMetricsService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.architecture.interfaces.rest.DddArchitectureCatalogController;
import com.lumira.saas.modules.competition.app.CertificateAppService;
import com.lumira.saas.modules.competition.app.CertificateRenderService;
import com.lumira.saas.modules.competition.app.CompetitionManagementAppService;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationAppService;
import com.lumira.saas.modules.competition.controller.CertificateV2Controller;
import com.lumira.saas.modules.competition.controller.CompetitionRegistrationV2Controller;
import com.lumira.saas.modules.competition.controller.CompetitionV2Controller;
import com.lumira.saas.modules.config.controller.HealthController;
import com.lumira.saas.modules.config.controller.VersionController;
import com.lumira.saas.modules.config.runtime.DatabaseVersionStartupRecorder;
import com.lumira.saas.modules.expert.app.ExpertManagementAppService;
import com.lumira.saas.modules.expert.app.ExpertApprovalEventConsumer;
import com.lumira.saas.modules.expert.controller.ExpertV2Controller;
import com.lumira.saas.modules.project.app.ProjectManagementAppService;
import com.lumira.saas.modules.project.controller.ProjectV2Controller;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import com.lumira.saas.modules.workflow.app.WorkflowSchemaBootstrap;
import com.lumira.saas.modules.workflow.controller.WorkflowV2Controller;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        ActivityManagementAppService.class,
        ActivityV2Controller.class,
        PublicActivityController.class,
        CertificateAppService.class,
        CertificateRenderService.class,
        CompetitionManagementAppService.class,
        CompetitionRegistrationAppService.class,
        CertificateV2Controller.class,
        CompetitionRegistrationV2Controller.class,
        CompetitionV2Controller.class,
        DatabaseVersionStartupRecorder.class,
        DddArchitectureCatalogController.class,
        ExpertApprovalEventConsumer.class,
        ExpertManagementAppService.class,
        ExpertV2Controller.class,
        HealthController.class,
        OwnerReadModelMetricsService.class,
        OwnerRuntimeMetrics.class,
        ProjectManagementAppService.class,
        ProjectV2Controller.class,
        WorkflowAppService.class,
        WorkflowSchemaBootstrap.class,
        WorkflowV2Controller.class,
        VersionController.class
})
public class SystemBusinessControlPlaneAssemblyConfiguration {
}
