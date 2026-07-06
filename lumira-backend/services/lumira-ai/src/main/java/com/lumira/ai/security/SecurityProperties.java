package com.lumira.ai.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas.security")
public class SecurityProperties {

    private String jwtSecret = "";
    private List<String> permitPaths = new ArrayList<>(List.of(
            "/api/version",
            "/api/v1/version",
            "/api/v1/*/version",
            "/actuator/health",
            "/actuator/info",
            "/error",
            "/api/v2/ai/readiness",
            "/api/v2/ai/health",
            "/api/v2/ai/metrics"
    ));

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public List<String> getPermitPaths() {
        return permitPaths;
    }

    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths;
    }
}
