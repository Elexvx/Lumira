package com.lumira.saas.modules.system.integration.workflow;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Explicit system-side adapters consumed by the Workflow bounded context. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        SystemWorkflowAuditAdapter.class,
        SystemWorkflowEventAdapter.class,
        SystemWorkflowUserAccessAdapter.class
})
public class SystemWorkflowIntegrationAssemblyConfiguration {
}
