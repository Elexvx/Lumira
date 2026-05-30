package com.legendary.invention.auth.config;

import com.legendary.invention.common.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuthSecurityProperties.class, WebProperties.class})
public class AuthBeansConfiguration {
}
