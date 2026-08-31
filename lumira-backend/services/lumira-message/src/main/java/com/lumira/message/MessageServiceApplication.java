package com.lumira.message;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.message",
        "com.lumira.common"
})
@MapperScan(basePackages = "com.lumira.message.mapper", annotationClass = Mapper.class)
public class MessageServiceApplication {
}
