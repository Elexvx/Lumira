package com.lumira.server;

import com.lumira.common.runtime.CommonRuntimeAssemblyConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(CommonRuntimeAssemblyConfiguration.class)
public class LumiraCommonRuntimeAssemblyConfiguration {
}
