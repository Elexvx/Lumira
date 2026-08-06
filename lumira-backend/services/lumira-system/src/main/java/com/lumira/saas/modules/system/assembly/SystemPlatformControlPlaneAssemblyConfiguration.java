package com.lumira.saas.modules.system.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.platform.app.PlatformBootstrapService;
import com.lumira.saas.modules.platform.controller.PlatformReadinessV2Controller;
import com.lumira.saas.modules.platform.controller.PlatformV2Controller;
import com.lumira.saas.modules.system.config.controller.SystemConfigVersionController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        PlatformBootstrapService.class,
        PlatformReadinessV2Controller.class,
        PlatformV2Controller.class,
        SystemConfigVersionController.class
})
public class SystemPlatformControlPlaneAssemblyConfiguration {
}
