package com.lumira.common.runtime;

import com.lumira.common.security.FieldCryptoService;
import com.lumira.common.security.InternalServiceTokenAuthFilter;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.ProductionSecurityPropertiesValidator;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.authorization.DefaultAuthorizationService;
import com.lumira.common.web.FeignHeaderForwardingConfig;
import com.lumira.common.web.ServiceVersionController;
import com.lumira.common.web.ServiceVersionProvider;
import com.lumira.common.web.WebProperties;
import com.lumira.common.web.config.CommonJacksonConfiguration;
import com.lumira.common.web.exception.GlobalExceptionHandler;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.common.web.repeatsubmit.RepeatSubmitAspect;
import com.lumira.common.web.repeatsubmit.RepeatSubmitStore;
import com.lumira.common.web.security.ErrorResponseSanitizer;
import com.lumira.common.web.security.RuntimeEnvironmentService;
import com.lumira.common.web.security.SensitiveErrorMessageSanitizer;
import com.lumira.common.web.security.audit.SecurityAuditEventService;
import com.lumira.common.web.security.headers.SecurityResponseHeadersFilter;
import com.lumira.common.web.security.ratelimit.RateLimitService;
import com.lumira.common.web.security.ratelimit.SecurityRateLimitFilter;
import com.lumira.common.web.security.ratelimit.SecurityRateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        WebProperties.class,
        SecurityRateLimitProperties.class
})
@Import({
        CommonJacksonConfiguration.class,
        FeignHeaderForwardingConfig.class,
        ServiceVersionController.class,
        ServiceVersionProvider.class,
        ReadModelVersionCache.class,
        RepeatSubmitStore.class,
        RepeatSubmitAspect.class,
        ClientIpResolver.class,
        GlobalExceptionHandler.class,
        DefaultAuthorizationService.class,
        SecurityContextFacade.class,
        ProductionSecurityPropertiesValidator.class,
        InternalServiceTokenAuthFilter.class,
        FieldCryptoService.class,
        PermissionGuard.class,
        SensitiveErrorMessageSanitizer.class,
        SecurityAuditEventService.class,
        RuntimeEnvironmentService.class,
        SecurityResponseHeadersFilter.class,
        ErrorResponseSanitizer.class,
        RateLimitService.class,
        SecurityRateLimitFilter.class
})
public class CommonRuntimeAssemblyConfiguration {
}
