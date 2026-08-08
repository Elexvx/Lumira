package com.lumira.file;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.file.controller.InternalJobController;
import com.lumira.file.event.FileOutboxRelay;
import com.lumira.file.event.LoggingFileOutboxDispatcher;
import com.lumira.file.event.RedisStreamFileOutboxDispatcher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Owner-side relay/replay surface used by the separate async runtime. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        LoggingFileOutboxDispatcher.class,
        RedisStreamFileOutboxDispatcher.class,
        FileOutboxRelay.class,
        InternalJobController.class
})
public class FileOwnerAsyncAdapterControlPlaneAssemblyConfiguration {
}
