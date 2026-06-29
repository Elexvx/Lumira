package com.lumira.localization;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.lumira.localization",
        "com.lumira.localization",
        "com.lumira.common"
})
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.localization",
        "com.lumira.localization",
        "com.lumira.common"
})
@MapperScan(basePackages = "com.lumira.localization.mapper", annotationClass = Mapper.class)
public class LocalizationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalizationServiceApplication.class, args);
    }
}
