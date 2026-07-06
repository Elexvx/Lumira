package com.lumira.payment;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.payment.config.PaymentSecurityConfig;
import com.lumira.payment.controller.InternalPaymentController;
import com.lumira.payment.controller.PaymentController;
import com.lumira.payment.controller.PaymentReadinessV2Controller;
import com.lumira.payment.controller.PaymentV2Controller;
import com.lumira.payment.service.PaymentInternalApiService;
import com.lumira.payment.security.JwtTokenService;
import com.lumira.payment.security.PaymentJwtAuthFilter;
import com.lumira.payment.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@EnableConfigurationProperties(SecurityProperties.class)
@Import({
        PaymentRuntimeAssemblyConfiguration.class,
        InternalPaymentController.class,
        JwtTokenService.class,
        PaymentController.class,
        PaymentInternalApiService.class,
        PaymentJwtAuthFilter.class,
        PaymentReadinessV2Controller.class,
        PaymentSecurityConfig.class,
        PaymentV2Controller.class
})
public class PaymentControlPlaneAssemblyConfiguration {
}
