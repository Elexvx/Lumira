package com.legendary.invention.plugin.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.plugin.config.TraceIdFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final TraceIdFilter traceIdFilter;
    private final PluginJwtAuthFilter pluginJwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(TraceIdFilter traceIdFilter, PluginJwtAuthFilter pluginJwtAuthFilter, ObjectMapper objectMapper) {
        this.traceIdFilter = traceIdFilter;
        this.pluginJwtAuthFilter = pluginJwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties securityProperties) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(securityProperties.getPermitPaths().toArray(new String[0])).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, authException) -> writeUnauthorizedResponse(request, response))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeForbiddenResponse(request, response)))
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(pluginJwtAuthFilter, TraceIdFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        BizException bizException = resolveAuthBizException(request);
        ErrorCode errorCode = bizException == null ? ErrorCode.UNAUTHORIZED : bizException.getErrorCode();
        ApiResponse<Void> body = bizException == null
                ? ApiResponse.fail(errorCode, TraceContext.getRequestId(), request.getRequestURI())
                : ApiResponse.fail(
                        errorCode,
                        bizException.getMessage(),
                        bizException.getUserMessage(),
                        TraceContext.getRequestId(),
                        request.getRequestURI()
                );
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void writeForbiddenResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.fail(ErrorCode.FORBIDDEN, TraceContext.getRequestId(), request.getRequestURI()))
        );
    }

    private BizException resolveAuthBizException(HttpServletRequest request) {
        Object attribute = request.getAttribute("pluginAuthBizException");
        if (attribute instanceof BizException bizException) {
            return bizException;
        }
        return null;
    }
}
