package com.legendary.invention.saas.infrastructure.http;

import com.legendary.invention.common.constant.HeaderConstants;
import com.legendary.invention.common.web.RequestContextUtils;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignHeaderForwardingConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            HttpServletRequest request = RequestContextUtils.currentRequest();
            if (request == null) {
                return;
            }
            copyHeader(request, template, HeaderConstants.REQUEST_ID);
            copyHeader(request, template, HeaderConstants.TENANT_ID);
            copyHeader(request, template, "X-Trace-Id");
            copyHeader(request, template, "Authorization");
        };
    }

    private void copyHeader(HttpServletRequest request, RequestTemplate template, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (headerValue != null && !headerValue.isBlank()) {
            template.header(headerName, headerValue);
        }
    }
}
