package com.legendary.invention.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {
        "com.legendary.invention.saas.common",
        "com.legendary.invention.saas.infrastructure",
        "com.legendary.invention.saas.modules.audit",
        "com.legendary.invention.saas.modules.iam",
        "com.legendary.invention.saas.modules.system.app"
})
@EnableDiscoveryClient
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
