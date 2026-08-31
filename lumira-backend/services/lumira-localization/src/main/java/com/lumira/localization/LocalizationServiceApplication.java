package com.lumira.localization;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.localization",
        "com.lumira.localization",
        "com.lumira.common"
})
@MapperScan(basePackages = "com.lumira.localization.mapper", annotationClass = Mapper.class)
public class LocalizationServiceApplication {
}
