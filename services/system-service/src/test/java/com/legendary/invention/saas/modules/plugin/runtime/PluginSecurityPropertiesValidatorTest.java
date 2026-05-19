package com.legendary.invention.saas.modules.plugin.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginSecurityPropertiesValidatorTest {

    @Test
    void rejectsDefaultSignatureSecretInProd() {
        PluginProperties properties = new PluginProperties();
        properties.setSignatureSecret("saas-plugin-signature-secret-dev-only");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        PluginSecurityPropertiesValidator validator = new PluginSecurityPropertiesValidator(properties, environment);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void acceptsStrongSignatureSecretInProd() {
        PluginProperties properties = new PluginProperties();
        properties.setSignatureSecret("prod-plugin-signature-secret-with-at-least-32-chars");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        PluginSecurityPropertiesValidator validator = new PluginSecurityPropertiesValidator(properties, environment);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void allowsDefaultSignatureSecretOutsideProd() {
        PluginProperties properties = new PluginProperties();
        properties.setSignatureSecret("saas-plugin-signature-secret-dev-only");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        PluginSecurityPropertiesValidator validator = new PluginSecurityPropertiesValidator(properties, environment);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }
}
