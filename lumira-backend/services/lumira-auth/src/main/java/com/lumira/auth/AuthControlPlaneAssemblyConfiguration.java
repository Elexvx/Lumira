package com.lumira.auth;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.auth.config.AuthBeansConfiguration;
import com.lumira.auth.config.AuthJwtAuthFilter;
import com.lumira.auth.config.AuthSecurityProperties;
import com.lumira.auth.controller.AuthController;
import com.lumira.auth.controller.AuthInternalController;
import com.lumira.auth.controller.AuthReadinessV2Controller;
import com.lumira.auth.controller.AuthV2Controller;
import com.lumira.auth.controller.AuthWechatCallbackController;
import com.lumira.auth.service.AuthAppService;
import com.lumira.auth.service.AuthCookieService;
import com.lumira.auth.service.AuthInternalApiService;
import com.lumira.auth.service.AuthSessionStore;
import com.lumira.auth.service.JwtTokenService;
import com.lumira.auth.service.LoginEncryptionService;
import com.lumira.auth.service.LoginProtectionService;
import com.lumira.auth.service.PasskeyAuthService;
import com.lumira.auth.service.SecuritySettingsService;
import com.lumira.auth.service.SystemAuthorizationSnapshotVersionVerifier;
import com.lumira.auth.service.WechatLoginService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@EnableConfigurationProperties(AuthSecurityProperties.class)
@Import({
        AuthBeansConfiguration.class,
        AuthJwtAuthFilter.class,
        AuthController.class,
        AuthInternalController.class,
        AuthReadinessV2Controller.class,
        AuthV2Controller.class,
        AuthWechatCallbackController.class,
        AuthAppService.class,
        AuthCookieService.class,
        AuthInternalApiService.class,
        AuthSessionStore.class,
        JwtTokenService.class,
        LoginEncryptionService.class,
        LoginProtectionService.class,
        PasskeyAuthService.class,
        SecuritySettingsService.class,
        SystemAuthorizationSnapshotVersionVerifier.class,
        WechatLoginService.class
})
public class AuthControlPlaneAssemblyConfiguration {

    @Bean
    FilterRegistrationBean<AuthJwtAuthFilter> disableAuthJwtServletFilterRegistration(AuthJwtAuthFilter filter) {
        FilterRegistrationBean<AuthJwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
