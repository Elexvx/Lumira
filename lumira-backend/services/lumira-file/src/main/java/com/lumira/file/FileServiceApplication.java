package com.lumira.file;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.file",
        "com.lumira.common"
})
@MapperScan(basePackages = "com.lumira.file.mapper", annotationClass = Mapper.class)
public class FileServiceApplication {
}
