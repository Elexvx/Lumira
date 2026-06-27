package com.lumira.server;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.file.FileRuntimeAssemblyConfiguration;
import com.lumira.file.controller.FileController;
import com.lumira.file.controller.FileReadinessV2Controller;
import com.lumira.file.controller.FileUploadMetrics;
import com.lumira.file.controller.FileV2Controller;
import com.lumira.file.controller.InternalFileController;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        FileRuntimeAssemblyConfiguration.class,
        FileController.class,
        FileReadinessV2Controller.class,
        FileUploadMetrics.class,
        FileV2Controller.class,
        InternalFileController.class
})
public class LumiraFileControlPlaneAssemblyConfiguration {
}
