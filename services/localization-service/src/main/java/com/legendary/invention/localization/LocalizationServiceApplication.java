package com.legendary.invention.localization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {
        "com.legendary.invention.saas.common",
        "com.legendary.invention.saas.infrastructure",
        "com.legendary.invention.saas.modules.iam",
        "com.legendary.invention.saas.modules.localization"
})
@EnableDiscoveryClient
public class LocalizationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalizationServiceApplication.class, args);
    }
}
