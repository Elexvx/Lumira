package com.lumira.saas.modules.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI's narrow view of the shared security configuration.
 *
 * <p>This is intentionally limited to secret material used to decrypt legacy
 * AI provider credentials.  HTTP security remains owned by the Admin runtime.
 * It does not create an AI-specific security filter chain.</p>
 */
@ConfigurationProperties(prefix = "saas.security")
public class AiSecurityProperties {

    private String jwtSecret = "";
    private String fieldSecret = "";

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getFieldSecret() {
        return fieldSecret;
    }

    public void setFieldSecret(String fieldSecret) {
        this.fieldSecret = fieldSecret;
    }
}
