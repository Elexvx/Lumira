package com.lumira.saas.modules.activity.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.activity.app.ActivityManagementAppService;
import com.lumira.saas.modules.activity.app.ActivityRegistrationAppService;
import com.lumira.saas.modules.activity.controller.ActivityRegistrationV2Controller;
import com.lumira.saas.modules.activity.controller.ActivityV2Controller;
import com.lumira.saas.modules.activity.controller.PublicActivityController;
import com.lumira.saas.modules.activity.infrastructure.JdbcActivityRepository;
import com.lumira.saas.modules.activity.integration.ActivityCatalogSourceSnapshotAdapter;
import com.lumira.saas.modules.activity.infrastructure.persistence.JdbcActivityRegistrationRepository;
import com.lumira.saas.modules.activity.infrastructure.persistence.JdbcActivitySqlOperations;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Explicit Activity ownership assembly for the modular-monolith server.
 * There is no physical Activity runtime: lumira-server imports this context.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        JdbcActivitySqlOperations.class,
        JdbcActivityRepository.class,
        ActivityCatalogSourceSnapshotAdapter.class,
        JdbcActivityRegistrationRepository.class,
        ActivityManagementAppService.class,
        ActivityRegistrationAppService.class,
        ActivityV2Controller.class,
        ActivityRegistrationV2Controller.class,
        PublicActivityController.class
})
public class ActivityControlPlaneAssemblyConfiguration {
}
