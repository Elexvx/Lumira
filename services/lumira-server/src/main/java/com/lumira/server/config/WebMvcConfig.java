package com.lumira.server.config;

import com.lumira.common.web.CorsMappingConfigurer;
import com.lumira.common.web.TraceIdFilter;
import com.lumira.common.web.WebProperties;
import com.lumira.file.config.UploadProperties;
import com.lumira.file.config.UploadResourceSecurityInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties({UploadProperties.class, WebProperties.class})
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;
    private final WebProperties webProperties;
    private final UploadResourceSecurityInterceptor uploadResourceSecurityInterceptor;

    public WebMvcConfig(
            UploadProperties uploadProperties,
            WebProperties webProperties,
            UploadResourceSecurityInterceptor uploadResourceSecurityInterceptor
    ) {
        this.uploadProperties = uploadProperties;
        this.webProperties = webProperties;
        this.uploadResourceSecurityInterceptor = uploadResourceSecurityInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsMappingConfigurer.addDefaultCorsMapping(registry, webProperties);
    }

    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path storageRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        registry.addResourceHandler(normalizePath(uploadProperties.getPublicPath()) + "/**")
                .addResourceLocations(storageRoot.toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(uploadResourceSecurityInterceptor)
                .addPathPatterns(normalizePath(uploadProperties.getPublicPath()) + "/**");
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/api/uploads";
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
