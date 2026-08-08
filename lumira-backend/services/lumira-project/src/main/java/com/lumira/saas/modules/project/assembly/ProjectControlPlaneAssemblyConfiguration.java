package com.lumira.saas.modules.project.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.project.app.ProjectManagementAppService;
import com.lumira.saas.modules.project.controller.ProjectV2Controller;
import com.lumira.saas.modules.project.infrastructure.JdbcProjectRepository;
import com.lumira.saas.modules.project.infrastructure.persistence.JdbcProjectSqlOperations;
import com.lumira.saas.modules.project.integration.ProjectSnapshotPortAdapter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Explicit Project ownership assembly for the modular-monolith server.
 * There is no physical Project runtime: lumira-server imports this context.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        JdbcProjectSqlOperations.class,
        JdbcProjectRepository.class,
        ProjectSnapshotPortAdapter.class,
        ProjectManagementAppService.class,
        ProjectV2Controller.class
})
public class ProjectControlPlaneAssemblyConfiguration {
}
