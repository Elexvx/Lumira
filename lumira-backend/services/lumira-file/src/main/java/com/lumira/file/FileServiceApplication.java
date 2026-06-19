package com.lumira.file;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.lumira.file",
        "com.lumira.common"
})
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.file",
        "com.lumira.common"
})
@MapperScan(basePackages = "com.lumira.file.mapper", annotationClass = Mapper.class)
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
