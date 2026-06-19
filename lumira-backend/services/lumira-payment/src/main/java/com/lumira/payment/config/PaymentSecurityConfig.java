package com.lumira.payment.config;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.security.InternalServiceTokenAuthFilter;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.TraceIdFilter;
import com.lumira.common.web.WebProperties;
import com.lumira.payment.security.PaymentJwtAuthFilter;
import com.lumira.payment.security.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties({SecurityProperties.class, WebProperties.class})
public class PaymentSecurityConfig {

    private final TraceIdFilter traceIdFilter;
    private final InternalServiceTokenAuthFilter internalServiceTokenAuthFilter;
    private final PaymentJwtAuthFilter paymentJwtAuthFilter;
    private final ObjectMapper objectMapper;

    public PaymentSecurityConfig(
            TraceIdFilter traceIdFilter,
            InternalServiceTokenAuthFilter internalServiceTokenAuthFilter,
            PaymentJwtAuthFilter paymentJwtAuthFilter,
            ObjectMapper objectMapper
    ) {
        this.traceIdFilter = traceIdFilter;
        this.internalServiceTokenAuthFilter = internalServiceTokenAuthFilter;
        this.paymentJwtAuthFilter = paymentJwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain paymentSecurityFilterChain(HttpSecurity http, SecurityProperties securityProperties) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(securityProperties.getPermitPaths().toArray(new String[0])).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, authException) -> writeResponse(request, response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeResponse(request, response, ErrorCode.FORBIDDEN)))
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(internalServiceTokenAuthFilter, TraceIdFilter.class)
                .addFilterAfter(paymentJwtAuthFilter, InternalServiceTokenAuthFilter.class);
        return http.build();
    }

    private void writeResponse(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(errorCode, TraceContext.getRequestId(), request.getRequestURI())
        ));
    }
}
