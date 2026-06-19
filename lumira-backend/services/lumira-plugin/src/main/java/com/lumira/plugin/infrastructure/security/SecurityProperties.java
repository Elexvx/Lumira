package com.lumira.plugin.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "saas.security")
public class SecurityProperties {

    private String jwtSecret = "";
    private List<String> permitPaths = new ArrayList<>();

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
