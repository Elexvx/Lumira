package com.lumira.saas.modules.system.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.architecture.application.OwnerReadModelMetricsService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.architecture.interfaces.rest.DddArchitectureCatalogController;
import com.lumira.saas.modules.config.controller.HealthController;
import com.lumira.saas.modules.config.controller.VersionController;
import com.lumira.saas.modules.config.runtime.DatabaseVersionStartupRecorder;
import com.lumira.saas.modules.draft.app.UserDraftAppService;
import com.lumira.saas.modules.draft.controller.UserDraftController;
import com.lumira.saas.modules.draft.infrastructure.JdbcUserDraftRepository;
import com.lumira.saas.modules.system.audit.infrastructure.JdbcSystemAuditQueryRepository;
import com.lumira.saas.modules.system.config.infrastructure.JdbcSystemConfigurationManagementRepository;
import com.lumira.saas.modules.system.dict.infrastructure.JdbcSystemDictionaryManagementRepository;
import com.lumira.saas.modules.system.infrastructure.SystemManagementPersistenceDependencies;
import com.lumira.saas.modules.system.menu.infrastructure.JdbcSystemMenuManagementRepository;
import com.lumira.saas.modules.system.profile.infrastructure.JdbcSystemCurrentUserProfileRepository;
import com.lumira.saas.modules.system.integration.workflow.SystemWorkflowIntegrationAssemblyConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        DatabaseVersionStartupRecorder.class,
        DddArchitectureCatalogController.class,
        JdbcUserDraftRepository.class,
        UserDraftAppService.class,
        UserDraftController.class,
        JdbcSystemAuditQueryRepository.class,
        JdbcSystemConfigurationManagementRepository.class,
        JdbcSystemDictionaryManagementRepository.class,
        JdbcSystemMenuManagementRepository.class,
        JdbcSystemCurrentUserProfileRepository.class,
        SystemManagementPersistenceDependencies.class,
        HealthController.class,
        OwnerReadModelMetricsService.class,
        OwnerRuntimeMetrics.class,
        SystemWorkflowIntegrationAssemblyConfiguration.class,
        VersionController.class
})
public class SystemBusinessControlPlaneAssemblyConfiguration {
}
