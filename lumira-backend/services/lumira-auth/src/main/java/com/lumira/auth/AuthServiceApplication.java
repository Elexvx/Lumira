package com.lumira.auth;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.auth",
        "com.lumira.common"
})
public class AuthServiceApplication {
}
