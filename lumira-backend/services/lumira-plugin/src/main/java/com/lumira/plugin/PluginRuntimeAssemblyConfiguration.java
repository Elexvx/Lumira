package com.lumira.plugin;

import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.modules.plugin.app.PluginManagementAppService;
import com.lumira.saas.modules.plugin.event.PluginDomainEventPublisher;
import com.lumira.saas.modules.plugin.event.PluginOutboxService;
import com.lumira.saas.modules.plugin.loader.PluginArtifactLoader;
import com.lumira.saas.modules.plugin.loader.PluginRuntimeLoader;
import com.lumira.saas.modules.plugin.mapper.PluginPersistenceMapper;
import com.lumira.saas.modules.plugin.registry.PluginRegistry;
import com.lumira.saas.modules.plugin.runtime.PluginProperties;
import com.lumira.saas.modules.plugin.runtime.PluginRuntimeSecurityPolicy;
import com.lumira.saas.modules.plugin.runtime.PluginSecurityPropertiesValidator;
import com.lumira.saas.modules.plugin.service.PluginMigrationService;
import com.lumira.saas.modules.plugin.service.PluginPersistenceService;
import com.lumira.saas.modules.plugin.service.PluginSemver;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PluginProperties.class)
@MapperScan(basePackageClasses = PluginPersistenceMapper.class, annotationClass = Mapper.class)
@Import({
        PluginArtifactLoader.class,
        PluginManagementAppService.class,
        PluginMigrationService.class,
        PluginOutboxService.class,
        PluginPersistenceService.class,
        PluginRegistry.class,
        PluginRuntimeLoader.class,
        PluginRuntimeSecurityPolicy.class,
        PluginSecurityPropertiesValidator.class,
        PluginSemver.class
})
public class PluginRuntimeAssemblyConfiguration {

    @Bean(name = "pluginDomainEventPublisher")
    public DomainEventPublisher pluginDomainEventPublisher(PluginOutboxService pluginOutboxService) {
        return new PluginDomainEventPublisher(pluginOutboxService);
    }
}
