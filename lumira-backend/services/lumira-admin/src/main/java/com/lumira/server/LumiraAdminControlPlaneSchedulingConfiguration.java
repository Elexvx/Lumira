package com.lumira.server;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@EnableScheduling
public class LumiraAdminControlPlaneSchedulingConfiguration {
}
