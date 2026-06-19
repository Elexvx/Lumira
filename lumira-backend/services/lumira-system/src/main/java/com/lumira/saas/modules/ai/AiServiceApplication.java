package com.lumira.saas.modules.ai;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.lumira.saas.modules.ai",
        "com.lumira.saas.infrastructure.persistence.mybatis",
        "com.lumira.saas.infrastructure.readmodel",
        "com.lumira.common"
})
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.saas.modules.ai",
        "com.lumira.common"
})
@MapperScan(basePackages = {
        "com.lumira.saas.modules.ai.mapper",
        "com.lumira.saas.infrastructure.persistence.mybatis"
}, annotationClass = Mapper.class)
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
