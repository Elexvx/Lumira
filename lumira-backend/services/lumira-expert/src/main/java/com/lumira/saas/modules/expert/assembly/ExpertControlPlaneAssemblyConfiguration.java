package com.lumira.saas.modules.expert.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.expert.app.ExpertApprovalEventConsumer;
import com.lumira.saas.modules.expert.app.ExpertManagementAppService;
import com.lumira.saas.modules.expert.controller.ExpertV2Controller;
import com.lumira.saas.modules.expert.infrastructure.JdbcExpertApprovalRepository;
import com.lumira.saas.modules.expert.infrastructure.JdbcExpertRepository;
import com.lumira.saas.modules.expert.infrastructure.persistence.JdbcExpertSqlOperations;
import com.lumira.saas.modules.expert.integration.account.ExpertAccountActivationAdapter;
import com.lumira.saas.modules.expert.integration.ExpertSnapshotPortAdapter;
import com.lumira.saas.modules.expert.integration.workflow.ExpertWorkflowExpertApplicationAdapter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Explicit aggregate-runtime assembly for the Expert bounded context. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        ExpertApprovalEventConsumer.class,
        ExpertManagementAppService.class,
        ExpertV2Controller.class,
        ExpertAccountActivationAdapter.class,
        ExpertSnapshotPortAdapter.class,
        ExpertWorkflowExpertApplicationAdapter.class,
        JdbcExpertApprovalRepository.class,
        JdbcExpertRepository.class,
        JdbcExpertSqlOperations.class
})
public class ExpertControlPlaneAssemblyConfiguration {
}
