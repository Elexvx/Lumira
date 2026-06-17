package com.lumira.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lumira.file.security-scan")
public class FileSecurityScanProperties {

    public enum Mode {
        INLINE,
        CLAMAV
    }

    private Mode mode = Mode.INLINE;
    private String clamavHost = "127.0.0.1";
    private int clamavPort = 3310;
    private int timeoutMillis = 3000;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.INLINE : mode;
    }

    public String getClamavHost() {
        return hasText(clamavHost) ? clamavHost : "127.0.0.1";
    }

    public void setClamavHost(String clamavHost) {
        this.clamavHost = clamavHost;
    }

    public int getClamavPort() {
        return clamavPort <= 0 ? 3310 : clamavPort;
    }

    public void setClamavPort(int clamavPort) {
        this.clamavPort = clamavPort;
    }

    public int getTimeoutMillis() {
        return timeoutMillis <= 0 ? 3000 : timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
