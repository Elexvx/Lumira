package com.lumira.plugin;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.lumira.plugin",
        "com.lumira.saas.modules.plugin",
        "com.lumira.common"
})
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.plugin",
        "com.lumira.saas.modules.plugin",
        "com.lumira.common"
})
@MapperScan(basePackages = "com.lumira.saas.modules.plugin.mapper", annotationClass = Mapper.class)
public class PluginServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PluginServiceApplication.class, args);
    }
}
