package com.lumira.auth.config;

import com.lumira.common.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuthSecurityProperties.class, WebProperties.class})
public class AuthBeansConfiguration {
}
