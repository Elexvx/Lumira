package com.legendary.invention.saas.infrastructure.http;

import com.legendary.invention.common.constant.HeaderConstants;
import com.legendary.invention.common.web.RequestContextUtils;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class FeignHeaderForwardingConfig {

    private final String internalToken;

    public FeignHeaderForwardingConfig(@Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            HttpServletRequest request = RequestContextUtils.currentRequest();
            if (request == null) {
                return;
            }
            boolean internalRequest = isInternalRequest(template);
            copyHeader(request, template, HeaderConstants.REQUEST_ID);
            copyHeader(request, template, HeaderConstants.TENANT_ID);
            copyHeader(request, template, "X-Trace-Id");
            if (internalRequest) {
                template.removeHeader("Authorization");
            } else {
                copyHeader(request, template, "Authorization");
            }
            if (internalRequest && StringUtils.hasText(internalToken)) {
                template.header("X-Job-Token", internalToken);
            }
        };
    }

    private boolean isInternalRequest(RequestTemplate template) {
        String path = template.path();
        if (StringUtils.hasText(path) && path.contains("/internal/")) {
            return true;
        }
        String url = template.url();
        return StringUtils.hasText(url) && url.contains("/internal/");
    }

    private void copyHeader(HttpServletRequest request, RequestTemplate template, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (headerValue != null && !headerValue.isBlank()) {
            template.header(headerName, headerValue);
        }
    }
}
