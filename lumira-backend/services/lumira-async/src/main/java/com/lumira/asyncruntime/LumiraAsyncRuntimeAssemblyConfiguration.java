package com.lumira.asyncruntime;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        LumiraAsyncCommonRuntimeAssemblyConfiguration.class,
        LumiraAsyncOwnerRelayAssemblyConfiguration.class
})
public class LumiraAsyncRuntimeAssemblyConfiguration {
}
