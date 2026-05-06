package com.legendary.invention.message.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({WebProperties.class, MessageProperties.class})
public class WebMvcConfig implements WebMvcConfigurer {

    private final WebProperties webProperties;

    public WebMvcConfig(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var registration = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id");
        if (!webProperties.getCorsAllowedOrigins().isEmpty()) {
            registration.allowedOrigins(webProperties.getCorsAllowedOrigins().toArray(new String[0]));
        } else if (!webProperties.getCorsAllowedOriginPatterns().isEmpty()) {
            registration.allowedOriginPatterns(webProperties.getCorsAllowedOriginPatterns().toArray(new String[0]));
        }
    }
}
