package com.lumira.saas.modules.system;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.system.assembly.SystemAuditControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemBusinessControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemIamControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemInfrastructureControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemOperationsControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemPlatformControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemUserControlPlaneAssemblyConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        SystemInfrastructureControlPlaneAssemblyConfiguration.class,
        SystemBusinessControlPlaneAssemblyConfiguration.class,
        SystemAuditControlPlaneAssemblyConfiguration.class,
        SystemIamControlPlaneAssemblyConfiguration.class,
        SystemPlatformControlPlaneAssemblyConfiguration.class,
        SystemOperationsControlPlaneAssemblyConfiguration.class,
        SystemUserControlPlaneAssemblyConfiguration.class
})
public class SystemControlPlaneAssemblyConfiguration {
}
