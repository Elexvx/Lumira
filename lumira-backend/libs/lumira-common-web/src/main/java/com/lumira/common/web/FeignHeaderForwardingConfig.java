package com.lumira.common.web;

import com.lumira.common.constant.HeaderConstants;
import com.lumira.common.security.InternalServiceTokenPolicy;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class FeignHeaderForwardingConfig {

    private final String internalToken;
    private final String systemToken;
    private final String authToken;
    private final String fileToken;
    private final String messageToken;
    private final String paymentToken;
    private final String pluginToken;
    private final String jobToken;

    public FeignHeaderForwardingConfig(@Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken) {
        this(internalToken, null, null, null, null, null, null, null);
    }

    @Autowired
    public FeignHeaderForwardingConfig(
            @Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken,
            @Value("${saas.internal.system-token:${SAAS_INTERNAL_SYSTEM_TOKEN:}}") String systemToken,
            @Value("${saas.internal.auth-token:${SAAS_INTERNAL_AUTH_TOKEN:}}") String authToken,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileToken,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String messageToken,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String paymentToken,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String pluginToken,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken
    ) {
        this.internalToken = internalToken;
        this.systemToken = systemToken;
        this.authToken = authToken;
        this.fileToken = fileToken;
        this.messageToken = messageToken;
        this.paymentToken = paymentToken;
        this.pluginToken = pluginToken;
        this.jobToken = jobToken;
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
            String tokenPath = StringUtils.hasText(template.path()) ? template.path() : template.url();
            String token = InternalServiceTokenPolicy.tokenForPath(tokenPath, internalToken, systemToken, authToken,
                    fileToken, messageToken, paymentToken, pluginToken, jobToken);
            if (internalRequest && StringUtils.hasText(token)) {
                template.removeHeader("X-Job-Token");
                template.header("X-Job-Token", token);
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
