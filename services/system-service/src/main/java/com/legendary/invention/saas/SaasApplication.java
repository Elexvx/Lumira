package com.legendary.invention.saas;

import com.legendary.invention.common.security.InternalServiceTokenAuthFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.legendary.invention.saas")
@EnableFeignClients(basePackages = "com.legendary.invention.api.client")
@EnableScheduling
@Import(InternalServiceTokenAuthFilter.class)
public class SaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaasApplication.class, args);
    }
}
