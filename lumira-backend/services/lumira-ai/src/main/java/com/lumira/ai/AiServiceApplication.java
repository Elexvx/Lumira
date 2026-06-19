package com.lumira.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.lumira.ai",
        "com.lumira.common"
})
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.ai",
        "com.lumira.common"
})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
