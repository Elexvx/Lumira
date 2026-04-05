package com.yourcompany.saas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaasApplication.class, args);
    }
}
