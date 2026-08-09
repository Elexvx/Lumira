package com.lumira.saas.modules.workflow.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import com.lumira.saas.modules.workflow.controller.WorkflowV2Controller;
import com.lumira.saas.modules.workflow.infrastructure.JdbcWorkflowRepository;
import com.lumira.saas.modules.workflow.infrastructure.persistence.JdbcWorkflowSqlOperations;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Explicit Workflow ownership assembly for the modular-monolith server.
 * Workflow remains part of lumira-server; no physical workflow runtime is
 * introduced by this module extraction.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        JdbcWorkflowSqlOperations.class,
        JdbcWorkflowRepository.class,
        WorkflowAppService.class,
        WorkflowV2Controller.class
})
public class WorkflowControlPlaneAssemblyConfiguration {
}
