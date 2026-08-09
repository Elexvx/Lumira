package com.lumira.saas.modules.expert.assembly;

import com.lumira.saas.modules.expert.app.ExpertApprovalEventConsumer;
import com.lumira.saas.modules.expert.app.ExpertManagementAppService;
import com.lumira.saas.modules.expert.controller.ExpertV2Controller;
import com.lumira.saas.modules.expert.integration.ExpertSnapshotPortAdapter;
import com.lumira.saas.modules.expert.integration.account.ExpertAccountActivationAdapter;
import com.lumira.saas.modules.expert.integration.workflow.ExpertWorkflowExpertApplicationAdapter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class ExpertControlPlaneAssemblyConfigurationTest {

    @Test
    void explicitlyAssemblesExpertEndpointsAndOwnerAdapters() {
        Import imported = ExpertControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(Arrays.asList(imported.value())).contains(
                ExpertManagementAppService.class,
                ExpertApprovalEventConsumer.class,
                ExpertV2Controller.class,
                ExpertAccountActivationAdapter.class,
                ExpertSnapshotPortAdapter.class,
                ExpertWorkflowExpertApplicationAdapter.class
        );
    }
}
