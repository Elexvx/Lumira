package com.lumira.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;

@SpringBootApplication(scanBasePackages = {"com.lumira"})
@ConfigurationPropertiesScan("com.lumira")
@MapperScan(basePackages = "com.lumira", annotationClass = Mapper.class)
public class LumiraServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumiraServerApplication.class, args);
    }
}
