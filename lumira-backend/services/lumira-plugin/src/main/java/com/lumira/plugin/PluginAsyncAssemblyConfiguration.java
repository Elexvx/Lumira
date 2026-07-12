package com.lumira.plugin;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.saas.modules.plugin.event.LoggingPluginOutboxDispatcher;
import com.lumira.saas.modules.plugin.event.PluginOutboxRelay;
import com.lumira.saas.modules.plugin.event.PluginOutboxService;
import com.lumira.saas.modules.plugin.event.RedisStreamPluginOutboxDispatcher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraAsyncEnabled
@Import({
        PluginOutboxService.class,
        LoggingPluginOutboxDispatcher.class,
        RedisStreamPluginOutboxDispatcher.class,
        PluginOutboxRelay.class,
        com.lumira.saas.modules.plugin.controller.InternalJobController.class
})
public class PluginAsyncAssemblyConfiguration {
}
