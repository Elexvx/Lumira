package com.lumira.asyncruntime;

import com.lumira.common.security.InternalServiceTokenAuthFilter;
import com.lumira.common.web.TraceIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration(proxyBeanMethods = false)
public class LumiraAsyncSecurityConfiguration {

    private final TraceIdFilter traceIdFilter;
    private final InternalServiceTokenAuthFilter internalServiceTokenAuthFilter;

    public LumiraAsyncSecurityConfiguration(
            TraceIdFilter traceIdFilter,
            InternalServiceTokenAuthFilter internalServiceTokenAuthFilter
    ) {
        this.traceIdFilter = traceIdFilter;
        this.internalServiceTokenAuthFilter = internalServiceTokenAuthFilter;
    }

    @Bean
    public SecurityFilterChain asyncSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/startup",
                                "/api/version",
                                "/api/v1/version",
                                "/api/v1/*/version",
                                "/error"
                        ).permitAll()
                        .requestMatchers(new RegexRequestMatcher(".*/internal/.*", null)).authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(internalServiceTokenAuthFilter, TraceIdFilter.class);
        return http.build();
    }
}
