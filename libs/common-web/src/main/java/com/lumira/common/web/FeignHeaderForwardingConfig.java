package com.lumira.common.web;

import com.lumira.common.constant.HeaderConstants;
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
            boolean internalRequest = isInternalRequest(template);
            HttpServletRequest request = RequestContextUtils.currentRequest();
            if (request != null) {
                copyHeader(request, template, HeaderConstants.REQUEST_ID);
                copyHeader(request, template, HeaderConstants.TRACE_ID);
                if (internalRequest) {
                    template.removeHeader(HeaderConstants.AUTHORIZATION);
                } else {
                    copyHeader(request, template, HeaderConstants.AUTHORIZATION);
                }
            }
            if (internalRequest && StringUtils.hasText(internalToken)) {
                template.removeHeader("X-Job-Token");
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
        if (StringUtils.hasText(url) && url.contains("/internal/")) {
            return true;
        }
        return template.feignTarget() != null
                && template.feignTarget().type() != null
                && template.feignTarget().type().getName().startsWith("com.lumira.api.client.");
    }

    private void copyHeader(HttpServletRequest request, RequestTemplate template, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (headerValue != null && !headerValue.isBlank()) {
            template.header(headerName, headerValue);
        }
    }
}
