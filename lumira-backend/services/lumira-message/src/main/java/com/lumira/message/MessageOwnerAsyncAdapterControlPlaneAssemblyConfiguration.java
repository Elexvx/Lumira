package com.lumira.message;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.message.controller.InternalJobController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Owner-side relay/replay surface used by the separate async runtime. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import(InternalJobController.class)
public class MessageOwnerAsyncAdapterControlPlaneAssemblyConfiguration {
}
