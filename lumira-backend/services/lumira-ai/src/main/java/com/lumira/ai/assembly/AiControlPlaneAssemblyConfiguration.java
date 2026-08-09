package com.lumira.ai.assembly;

import com.lumira.ai.compat.AiV2CompatibilityFacade;
import com.lumira.ai.controller.AiReadinessV2Controller;
import com.lumira.ai.controller.AiV2Controller;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.ai.config.AiSecurityProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Explicit AI control-plane assembly for the modular-monolith Admin runtime.
 *
 * <p>{@code ai-service} is an owner module, not a fourth independently
 * deployed process.  The only production server imports this configuration
 * from {@code lumira-server}'s Admin assembly.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@EnableConfigurationProperties(AiSecurityProperties.class)
@ComponentScan(basePackages = "com.lumira.saas.modules.ai")
@MapperScan(
        basePackages = "com.lumira.saas.modules.ai.infrastructure.persistence.support",
        annotationClass = Mapper.class
)
@Import({
        AiV2CompatibilityFacade.class,
        AiV2Controller.class,
        AiReadinessV2Controller.class
})
public class AiControlPlaneAssemblyConfiguration {
}
