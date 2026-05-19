package com.legendary.invention.file;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {
        "com.legendary.invention.common",
        "com.legendary.invention.api",
        "com.legendary.invention.saas.infrastructure.config",
        "com.legendary.invention.file"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.legendary.invention.api.client")
@MapperScan("com.legendary.invention.file")
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
