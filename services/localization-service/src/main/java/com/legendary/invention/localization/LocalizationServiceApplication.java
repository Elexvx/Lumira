package com.legendary.invention.localization;

import com.legendary.invention.common.security.InternalServiceTokenAuthFilter;
import com.legendary.invention.common.web.TraceIdFilter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {
        "com.legendary.invention.localization",
        "com.legendary.invention.saas.modules.localization",
        "com.legendary.invention.common.security",
        "com.legendary.invention.common.web"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.legendary.invention.api.client")
@Import({InternalServiceTokenAuthFilter.class, TraceIdFilter.class})
@MapperScan("com.legendary.invention.saas.modules.localization")
public class LocalizationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalizationServiceApplication.class, args);
    }
}
