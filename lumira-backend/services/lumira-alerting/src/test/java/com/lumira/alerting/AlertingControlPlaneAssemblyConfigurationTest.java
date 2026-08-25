package com.lumira.alerting;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.alerting.app.AlertingAppService;
import com.lumira.alerting.app.AlertingJobService;
import com.lumira.alerting.controller.AlertingController;
import com.lumira.alerting.controller.AlertingInternalJobController;
import com.lumira.alerting.infrastructure.AlertDeliveryGateway;
import com.lumira.alerting.infrastructure.AlertingRepository;
import com.lumira.alerting.infrastructure.AlertingSecretCrypto;
import com.lumira.alerting.support.BuiltinAlertingLifecycleHook;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

class AlertingControlPlaneAssemblyConfigurationTest {
    @Test
    void explicitlyAssemblesAlertingOwnedComponents() {
        Import imported = AlertingControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(Arrays.asList(imported.value())).containsExactlyInAnyOrder(
                AlertingRepository.class,
                AlertingSecretCrypto.class,
                AlertDeliveryGateway.class,
                AlertingAppService.class,
                AlertingJobService.class,
                AlertingController.class,
                AlertingInternalJobController.class,
                BuiltinAlertingLifecycleHook.class
        );
    }
}
