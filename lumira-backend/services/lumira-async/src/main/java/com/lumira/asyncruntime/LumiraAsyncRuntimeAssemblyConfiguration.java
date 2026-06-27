package com.lumira.asyncruntime;

import com.lumira.message.MessageAsyncAssemblyConfiguration;
import com.lumira.payment.PaymentAsyncAssemblyConfiguration;
import com.lumira.plugin.PluginAsyncAssemblyConfiguration;
import com.lumira.saas.modules.system.SystemAsyncAssemblyConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        LumiraAsyncCommonRuntimeAssemblyConfiguration.class,
        LumiraFileAsyncAssemblyConfiguration.class,
        SystemAsyncAssemblyConfiguration.class,
        MessageAsyncAssemblyConfiguration.class,
        PaymentAsyncAssemblyConfiguration.class,
        PluginAsyncAssemblyConfiguration.class
})
public class LumiraAsyncRuntimeAssemblyConfiguration {
}
