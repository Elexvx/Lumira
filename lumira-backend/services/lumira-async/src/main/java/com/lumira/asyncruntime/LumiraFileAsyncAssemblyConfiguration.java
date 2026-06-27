package com.lumira.asyncruntime;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.file.FileRuntimeAssemblyConfiguration;
import com.lumira.file.controller.InternalJobController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraAsyncEnabled
@Import({
        FileRuntimeAssemblyConfiguration.class,
        InternalJobController.class
})
public class LumiraFileAsyncAssemblyConfiguration {
}
