package com.legendary.invention.saas.infrastructure.security;

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
                "strong-database-password",
                "prod"
        );

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsDefaultDatabasePasswordInProd() {
        RuntimeSecurityPropertiesValidator validator = buildValidator(
                "prod-jwt-secret-with-at-least-32-characters",
                "123456",
                "prod"
        );

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void acceptsStrongSecretsInProd() {
        RuntimeSecurityPropertiesValidator validator = buildValidator(
                "prod-jwt-secret-with-at-least-32-characters",
                "prod-database-password",
                "prod"
        );

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void allowsDefaultsOutsideProd() {
        RuntimeSecurityPropertiesValidator validator = buildValidator(
                "saas_foundation_jwt_secret_for_dev_env_please_change_me_2026",
                "123456",
                "dev"
        );

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    private RuntimeSecurityPropertiesValidator buildValidator(String jwtSecret, String databasePassword, String profile) {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setJwtSecret(jwtSecret);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        environment.setProperty("spring.datasource.password", databasePassword);
        return new RuntimeSecurityPropertiesValidator(securityProperties, environment);
    }
}
