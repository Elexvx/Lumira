package com.lumira.saas.infrastructure.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas.redis")
public class RedisStartupCleanupProperties {

    private boolean enabled = true;
    private boolean clearOnStartup = false;
    private boolean allowClearOnStartupInProd = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isClearOnStartup() {
        return clearOnStartup;
    }

    public void setClearOnStartup(boolean clearOnStartup) {
        this.clearOnStartup = clearOnStartup;
    }

    public boolean isAllowClearOnStartupInProd() {
        return allowClearOnStartupInProd;
    }

    public void setAllowClearOnStartupInProd(boolean allowClearOnStartupInProd) {
        this.allowClearOnStartupInProd = allowClearOnStartupInProd;
    }
}
