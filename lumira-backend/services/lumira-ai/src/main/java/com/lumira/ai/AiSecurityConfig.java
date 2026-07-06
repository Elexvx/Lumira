package com.lumira.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.ai.security.AiJwtAuthFilter;
import com.lumira.ai.security.SecurityProperties;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.security.InternalServiceTokenAuthFilter;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.TraceIdFilter;
import com.lumira.common.web.WebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

@Configuration
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties({SecurityProperties.class, WebProperties.class})
public class AiSecurityConfig {

    private final TraceIdFilter traceIdFilter;
    private final InternalServiceTokenAuthFilter internalServiceTokenAuthFilter;
    private final AiJwtAuthFilter aiJwtAuthFilter;
    private final ObjectMapper objectMapper;

    public AiSecurityConfig(
            TraceIdFilter traceIdFilter,
            InternalServiceTokenAuthFilter internalServiceTokenAuthFilter,
            AiJwtAuthFilter aiJwtAuthFilter,
            ObjectMapper objectMapper
    ) {
        this.traceIdFilter = traceIdFilter;
        this.internalServiceTokenAuthFilter = internalServiceTokenAuthFilter;
        this.aiJwtAuthFilter = aiJwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain aiSecurityFilterChain(HttpSecurity http, SecurityProperties securityProperties) throws Exception {
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
                .addFilterAfter(aiJwtAuthFilter, InternalServiceTokenAuthFilter.class);
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
