package com.legendary.invention.auth.config;

import com.legendary.invention.auth.filter.JwtAuthFilter;
import com.legendary.invention.common.security.InternalServiceTokenAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
public class SecurityConfig {

    private static final String[] DEFAULT_PUBLIC_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/login-encryption-key",
            "/api/v1/auth/login/code/challenge",
            "/api/v1/auth/login/code/complete",
            "/api/v1/auth/wechat/authorize-url",
            "/api/v1/auth/wechat/login",
            "/api/v1/auth/passkeys/authentication/options",
            "/api/v1/auth/passkeys/authentication/complete",
            "/api/v1/auth/second-factor/complete",
            "/api/v1/auth/refresh-token",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            InternalServiceTokenAuthFilter internalServiceTokenAuthFilter,
            SecurityProperties securityProperties
    ) throws Exception {
        String[] publicPaths = resolvePublicPaths(securityProperties.getPermitPaths());
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(publicPaths).permitAll()
                        .requestMatchers("/internal/**").authenticated()
                        .anyRequest().authenticated())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(internalServiceTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, InternalServiceTokenAuthFilter.class)
                .build();
    }

    private String[] resolvePublicPaths(List<String> configuredPaths) {
        if (configuredPaths == null || configuredPaths.isEmpty()) {
            return DEFAULT_PUBLIC_PATHS;
        }
        return configuredPaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(String::trim)
                .distinct()
                .toArray(String[]::new);
    }
}
