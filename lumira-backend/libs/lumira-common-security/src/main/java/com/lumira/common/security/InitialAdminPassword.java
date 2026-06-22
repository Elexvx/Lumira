package com.lumira.common.security;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public final class InitialAdminPassword {

    public static final String DEFAULT_PASSWORD = "123456";
    public static final String PROPERTY_NAME = "saas.security.initial-admin.password";
    public static final String ENVIRONMENT_VARIABLE_NAME = "LUMIRA_INITIAL_ADMIN_PASSWORD";

    private InitialAdminPassword() {
    }

    public static String resolve(Environment environment) {
        if (environment == null) {
            return DEFAULT_PASSWORD;
        }
        String explicitEnvironmentValue = environment.getProperty(ENVIRONMENT_VARIABLE_NAME);
        if (StringUtils.hasText(explicitEnvironmentValue)) {
            return explicitEnvironmentValue.trim();
        }
        String configuredValue = environment.getProperty(PROPERTY_NAME);
        if (StringUtils.hasText(configuredValue)) {
            return configuredValue.trim();
        }
        return DEFAULT_PASSWORD;
    }
}
