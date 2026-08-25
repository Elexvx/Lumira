package com.lumira.alerting;

import com.lumira.alerting.app.AlertingAppService;
import com.lumira.alerting.app.AlertingJobService;
import com.lumira.alerting.controller.AlertingController;
import com.lumira.alerting.controller.AlertingInternalJobController;
import com.lumira.alerting.infrastructure.AlertDeliveryGateway;
import com.lumira.alerting.infrastructure.AlertingRepository;
import com.lumira.alerting.infrastructure.AlertingSecretCrypto;
import com.lumira.alerting.support.BuiltinAlertingLifecycleHook;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        AlertingRepository.class,
        AlertingSecretCrypto.class,
        AlertDeliveryGateway.class,
        AlertingAppService.class,
        AlertingJobService.class,
        AlertingController.class,
        AlertingInternalJobController.class,
        BuiltinAlertingLifecycleHook.class
})
public class AlertingControlPlaneAssemblyConfiguration {
}
