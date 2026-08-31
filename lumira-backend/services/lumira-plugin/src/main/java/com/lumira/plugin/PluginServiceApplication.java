package com.lumira.plugin;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.plugin",
        "com.lumira.saas.modules.plugin",
        "com.lumira.common"
})
@MapperScan(basePackages = "com.lumira.saas.modules.plugin.mapper", annotationClass = Mapper.class)
public class PluginServiceApplication {
}
