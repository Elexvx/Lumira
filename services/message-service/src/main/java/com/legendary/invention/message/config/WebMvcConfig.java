package com.legendary.invention.message.config;

import com.legendary.invention.common.web.CorsMappingConfigurer;
import com.legendary.invention.common.web.TraceIdFilter;
import com.legendary.invention.common.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
        CorsMappingConfigurer.addDefaultCorsMapping(registry, webProperties);
    }

    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }
}
