package com.lumira.plugin;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.plugin.infrastructure.security.JwtTokenService;
import com.lumira.plugin.infrastructure.security.PluginJwtAuthFilter;
import com.lumira.plugin.infrastructure.security.SecurityProperties;
import com.lumira.saas.modules.plugin.controller.PluginManagementController;
import com.lumira.saas.modules.plugin.controller.PluginReadinessV2Controller;
import com.lumira.saas.modules.plugin.controller.PluginV2Controller;
import com.lumira.saas.modules.plugin.gateway.PluginGatewayController;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@EnableConfigurationProperties(SecurityProperties.class)
@Import({
        PluginRuntimeAssemblyConfiguration.class,
        JwtTokenService.class,
        PluginGatewayController.class,
        PluginJwtAuthFilter.class,
        PluginManagementController.class,
        PluginReadinessV2Controller.class,
        PluginV2Controller.class
})
public class PluginControlPlaneAssemblyConfiguration {
}
