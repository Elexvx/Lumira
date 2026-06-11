package com.lumira.saas.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeSecurityPropertiesValidatorTest {

    @Test
    void rejectsDefaultJwtSecretInProd() {
        RuntimeSecurityPropertiesValidator validator = buildValidator(
                "saas_foundation_jwt_secret_for_dev_env_please_change_me_2026",
                "prod-field-secret-with-at-least-32-characters",
                "strong-database-password",
                "https://saas.elexvx.com",
                "prod"
        );

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsDefaultDatabasePasswordInProd() {
        RuntimeSecurityPropertiesValidator validator = buildValidator(
                "prod-jwt-secret-with-at-least-32-characters",
                "prod-field-secret-with-at-least-32-characters",
                "123456",
                "https://saas.elexvx.com",
                "prod"
        );

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void acceptsStrongSecretsInProd() {
        RuntimeSecurityPropertiesValidator validator = buildValidator(
                "prod-jwt-secret-with-at-least-32-characters",
                "prod-field-secret-with-at-least-32-characters",
                "prod-database-password",
                "https://saas.elexvx.com",
                "prod"
        );

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void allowsDefaultsOutsideProd() {
        RuntimeSecurityPropertiesValidator validator = buildValidator(
                "saas_foundation_jwt_secret_for_dev_env_please_change_me_2026",
                "saas_foundation_field_secret_for_dev_env_please_change_me_2026",
                "123456",
                "http://localhost:*",
                "dev"
        );

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsMissingFieldSecretInProd() {
        RuntimeSecurityPropertiesValidator validator = buildValidator(
                "prod-jwt-secret-with-at-least-32-characters",
                "",
                "prod-database-password",
                "https://saas.elexvx.com",
                "prod"
        );

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsLocalhostCorsInProd() {
        RuntimeSecurityPropertiesValidator validator = buildValidator(
                "prod-jwt-secret-with-at-least-32-characters",
                "prod-field-secret-with-at-least-32-characters",
                "prod-database-password",
                "http://localhost:8000,https://saas.elexvx.com",
                "prod"
        );

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
    }

    private RuntimeSecurityPropertiesValidator buildValidator(
            String jwtSecret,
            String fieldSecret,
            String databasePassword,
            String corsAllowedOrigins,
            String profile
    ) {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setJwtSecret(jwtSecret);
        securityProperties.setFieldSecret(fieldSecret);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        environment.setProperty("spring.datasource.password", databasePassword);
        environment.setProperty("saas.web.cors-allowed-origin-patterns", corsAllowedOrigins);
        return new RuntimeSecurityPropertiesValidator(securityProperties, environment);
    }
}
