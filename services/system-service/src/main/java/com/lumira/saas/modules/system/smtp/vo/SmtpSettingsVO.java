package com.lumira.saas.modules.system.smtp.vo;

public class SmtpSettingsVO {

    private Boolean enabled;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String from;
    private Boolean authEnabled;
    private Boolean startTlsEnabled;
    private Boolean sslEnabled;
    private Boolean configured;
    private Boolean passwordConfigured;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public Boolean getAuthEnabled() { return authEnabled; }
    public void setAuthEnabled(Boolean authEnabled) { this.authEnabled = authEnabled; }
    public Boolean getStartTlsEnabled() { return startTlsEnabled; }
    public void setStartTlsEnabled(Boolean startTlsEnabled) { this.startTlsEnabled = startTlsEnabled; }
    public Boolean getSslEnabled() { return sslEnabled; }
    public void setSslEnabled(Boolean sslEnabled) { this.sslEnabled = sslEnabled; }
    public Boolean getConfigured() { return configured; }
    public void setConfigured(Boolean configured) { this.configured = configured; }
    public Boolean getPasswordConfigured() { return passwordConfigured; }
    public void setPasswordConfigured(Boolean passwordConfigured) { this.passwordConfigured = passwordConfigured; }
}
