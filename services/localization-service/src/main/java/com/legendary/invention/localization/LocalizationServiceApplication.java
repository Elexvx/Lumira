package com.legendary.invention.localization;

import com.legendary.invention.common.security.InternalServiceTokenAuthFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(scanBasePackages = {
        "com.legendary.invention.localization",
        "com.legendary.invention.saas"
})
@MapperScan({
        "com.legendary.invention.saas.infrastructure.event",
        "com.legendary.invention.saas.modules"
})
@EnableDiscoveryClient
@Import(InternalServiceTokenAuthFilter.class)
public class LocalizationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalizationServiceApplication.class, args);
    }
}
