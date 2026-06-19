package com.lumira.message;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.lumira.message",
        "com.lumira.common"
})
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.message",
        "com.lumira.common"
})
@MapperScan(basePackages = "com.lumira.message.mapper", annotationClass = Mapper.class)
public class MessageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageServiceApplication.class, args);
    }
}
