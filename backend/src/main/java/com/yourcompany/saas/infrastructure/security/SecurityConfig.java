package com.yourcompany.saas.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.observability.TraceIdFilter;
import com.yourcompany.saas.infrastructure.tenant.TenantFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final TraceIdFilter traceIdFilter;
    private final TenantFilter tenantFilter;
    private final JwtAuthFilter jwtAuthFilter;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            TraceIdFilter traceIdFilter,
            TenantFilter tenantFilter,
            JwtAuthFilter jwtAuthFilter,
            SecurityProperties securityProperties,
            ObjectMapper objectMapper
    ) {
        this.traceIdFilter = traceIdFilter;
        this.tenantFilter = tenantFilter;
        this.jwtAuthFilter = jwtAuthFilter;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(securityProperties.getPermitPaths().toArray(new String[0]))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, authException) ->
                                writeUnauthorizedResponse(request, response))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeForbiddenResponse(request, response)))
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, TraceIdFilter.class)
                .addFilterAfter(tenantFilter, JwtAuthFilter.class);
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
                        bizException.getErrorMessage(),
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
        Object attribute = request.getAttribute(JwtAuthFilter.AUTH_BIZ_EXCEPTION_ATTR);
        if (attribute instanceof BizException bizException) {
            return bizException;
        }
        return null;
    }
}
