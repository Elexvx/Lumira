package com.lumira.common.web.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
public class RuntimeEnvironmentService {

    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production", "cloud");

    private final Environment environment;

    public RuntimeEnvironmentService(Environment environment) {
        this.environment = environment;
    }

    public boolean isProduction() {
        return Arrays.stream(environment.getActiveProfiles())
                .filter(StringUtils::hasText)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(PRODUCTION_PROFILES::contains);
    }
}
