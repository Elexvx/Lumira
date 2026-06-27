package com.lumira.asyncruntime;

import com.lumira.common.runtime.CommonRuntimeAssemblyConfiguration;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.web.TraceIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraAsyncEnabled
@Import(CommonRuntimeAssemblyConfiguration.class)
public class LumiraAsyncCommonRuntimeAssemblyConfiguration {

    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }
}
