package com.lumira.plugin;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.plugin.controller.InternalJobController;
import com.lumira.saas.modules.plugin.event.LoggingPluginOutboxDispatcher;
import com.lumira.saas.modules.plugin.event.PluginOutboxRelay;
import com.lumira.saas.modules.plugin.event.RedisStreamPluginOutboxDispatcher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Owner-side relay/replay surface used by the separate async runtime. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        LoggingPluginOutboxDispatcher.class,
        RedisStreamPluginOutboxDispatcher.class,
        PluginOutboxRelay.class,
        InternalJobController.class
})
public class PluginOwnerAsyncAdapterControlPlaneAssemblyConfiguration {
}
