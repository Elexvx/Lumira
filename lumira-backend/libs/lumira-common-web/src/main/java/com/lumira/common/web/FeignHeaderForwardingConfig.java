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

    private final String systemToken;
    private final String authToken;
    private final String authSystemToken;
    private final String fileToken;
    private final String messageToken;
    private final String paymentToken;
    private final String pluginToken;
    private final String teamToken;
    private final String jobToken;

    private static final String JOB_TOKEN_HEADER = "X-Job-Token";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String FORWARDED_INTERNAL_TOKEN_HEADER = "X-Forwarded-Internal-Token";
    private static final String COOKIE_HEADER = "Cookie";

    public FeignHeaderForwardingConfig(@Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken) {
        this(null, null, null, null, null, null, null, null, jobToken);
    }

    public FeignHeaderForwardingConfig(
            String systemToken,
            String authToken,
            String authSystemToken,
            String fileToken,
            String messageToken,
            String paymentToken,
            String pluginToken,
            String jobToken
    ) {
        this(systemToken, authToken, authSystemToken, fileToken, messageToken, paymentToken, pluginToken, null, jobToken);
    }

    @Autowired
    public FeignHeaderForwardingConfig(
            @Value("${saas.internal.system-token:${SAAS_INTERNAL_SYSTEM_TOKEN:}}") String systemToken,
            @Value("${saas.internal.auth-token:${SAAS_INTERNAL_AUTH_TOKEN:}}") String authToken,
            @Value("${saas.internal.auth-system-token:${SAAS_INTERNAL_AUTH_SYSTEM_TOKEN:}}") String authSystemToken,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileToken,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String messageToken,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String paymentToken,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String pluginToken,
            @Value("${saas.internal.team-token:${SAAS_INTERNAL_TEAM_TOKEN:}}") String teamToken,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken
    ) {
        this.systemToken = firstText(systemToken, "SAAS_INTERNAL_SYSTEM_TOKEN");
        this.authToken = firstText(authToken, "SAAS_INTERNAL_AUTH_TOKEN");
        this.authSystemToken = firstText(authSystemToken, "SAAS_INTERNAL_AUTH_SYSTEM_TOKEN");
        this.fileToken = firstText(fileToken, "SAAS_INTERNAL_FILE_TOKEN");
        this.messageToken = firstText(messageToken, "SAAS_INTERNAL_MESSAGE_TOKEN");
        this.paymentToken = firstText(paymentToken, "SAAS_INTERNAL_PAYMENT_TOKEN");
        this.pluginToken = firstText(pluginToken, "SAAS_INTERNAL_PLUGIN_TOKEN");
        this.teamToken = firstText(teamToken, "SAAS_INTERNAL_TEAM_TOKEN");
        this.jobToken = firstText(jobToken, "SAAS_INTERNAL_JOB_TOKEN");
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            boolean internalRequest = isInternalRequest(template);
            removeInternalTokenHeaders(template);
            HttpServletRequest request = RequestContextUtils.currentRequest();
            if (request != null) {
                copyHeader(request, template, HeaderConstants.REQUEST_ID);
                copyHeader(request, template, HeaderConstants.TRACE_ID);
                if (internalRequest) {
                    template.removeHeader(HeaderConstants.AUTHORIZATION);
                    template.removeHeader(COOKIE_HEADER);
                } else if (!isAbsoluteExternalRequest(template)) {
                    copyHeader(request, template, HeaderConstants.AUTHORIZATION);
                } else {
                    template.removeHeader(HeaderConstants.AUTHORIZATION);
                    template.removeHeader(COOKIE_HEADER);
                }
            }
            String tokenPath = StringUtils.hasText(template.url()) ? template.url() : template.path();
            String token = InternalServiceTokenPolicy.tokenForPath(tokenPath, systemToken, authToken,
                    authSystemToken,
                    fileToken, messageToken, paymentToken, pluginToken, teamToken, jobToken);
            if (internalRequest && StringUtils.hasText(token)) {
                template.header(JOB_TOKEN_HEADER, token);
            }
        };
    }

    private boolean isInternalRequest(RequestTemplate template) {
        boolean internalClient = isInternalFeignClient(template);
        String url = template.url();
        if (StringUtils.hasText(url) && isAbsoluteUrl(url)) {
            return internalClient;
        }
        String path = template.path();
        if (StringUtils.hasText(path) && !isAbsoluteUrl(path) && path.contains("/internal/")) {
            return true;
        }
        if (StringUtils.hasText(url) && !isAbsoluteUrl(url) && url.contains("/internal/")) {
            return true;
        }
        return internalClient;
    }

    private boolean isAbsoluteExternalRequest(RequestTemplate template) {
        boolean internalClient = isInternalFeignClient(template);
        String url = template.url();
        String path = template.path();
        return !internalClient
                && ((StringUtils.hasText(url) && isAbsoluteUrl(url))
                || (StringUtils.hasText(path) && isAbsoluteUrl(path)));
    }

    private boolean isInternalFeignClient(RequestTemplate template) {
        return template.feignTarget() != null
                && template.feignTarget().type() != null
                && template.feignTarget().type().getName().startsWith("com.lumira.api.client.");
    }

    private boolean isAbsoluteUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private void copyHeader(HttpServletRequest request, RequestTemplate template, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (headerValue != null && !headerValue.isBlank()) {
            template.removeHeader(headerName);
            template.header(headerName, headerValue);
        }
    }

    private void removeInternalTokenHeaders(RequestTemplate template) {
        template.removeHeader(JOB_TOKEN_HEADER);
        template.removeHeader(INTERNAL_TOKEN_HEADER);
        template.removeHeader(FORWARDED_INTERNAL_TOKEN_HEADER);
    }

    private static String firstText(String value, String environmentVariable) {
        if (StringUtils.hasText(value)) {
            return value;
        }
        return System.getenv(environmentVariable);
    }
}
