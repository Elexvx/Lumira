package com.lumira.payment;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.payment",
        "com.lumira.common"
})
public class PaymentServiceApplication {
}
