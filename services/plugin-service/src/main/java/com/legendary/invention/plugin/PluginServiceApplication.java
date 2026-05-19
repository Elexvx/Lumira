package com.legendary.invention.plugin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "com.legendary.invention.common",
        "com.legendary.invention.api",
        "com.legendary.invention.saas.infrastructure.config",
        "com.legendary.invention.plugin",
        "com.legendary.invention.saas.modules.plugin"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.legendary.invention.api.client")
@MapperScan({
        "com.legendary.invention.plugin",
        "com.legendary.invention.saas.modules.plugin"
})
public class PluginServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PluginServiceApplication.class, args);
    }
}
