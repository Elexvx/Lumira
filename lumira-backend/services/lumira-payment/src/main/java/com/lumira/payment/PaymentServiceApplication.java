package com.lumira.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.lumira.payment",
        "com.lumira.common"
})
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.payment",
        "com.lumira.common"
})
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
