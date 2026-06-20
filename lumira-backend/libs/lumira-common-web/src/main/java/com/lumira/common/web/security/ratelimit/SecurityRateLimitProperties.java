package com.lumira.common.web.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.rate-limit")
public class SecurityRateLimitProperties {

    private boolean enabled = true;
    private Rule login = new Rule(10, 300);
    private Rule refresh = new Rule(30, 300);
    private Rule webhook = new Rule(120, 60);
    private Rule upload = new Rule(60, 60);
    private Rule pluginGateway = new Rule(120, 60);
    private Rule remoteStorageTest = new Rule(20, 300);
    private Rule aiTool = new Rule(30, 60);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Rule getLogin() { return login; }
    public void setLogin(Rule login) { this.login = login; }
    public Rule getRefresh() { return refresh; }
    public void setRefresh(Rule refresh) { this.refresh = refresh; }
    public Rule getWebhook() { return webhook; }
    public void setWebhook(Rule webhook) { this.webhook = webhook; }
    public Rule getUpload() { return upload; }
    public void setUpload(Rule upload) { this.upload = upload; }
    public Rule getPluginGateway() { return pluginGateway; }
    public void setPluginGateway(Rule pluginGateway) { this.pluginGateway = pluginGateway; }
    public Rule getRemoteStorageTest() { return remoteStorageTest; }
    public void setRemoteStorageTest(Rule remoteStorageTest) { this.remoteStorageTest = remoteStorageTest; }
    public Rule getAiTool() { return aiTool; }
    public void setAiTool(Rule aiTool) { this.aiTool = aiTool; }

    public static class Rule {
        private int maxAttempts;
        private long windowSeconds;

        public Rule() {
        }

        public Rule(int maxAttempts, long windowSeconds) {
            this.maxAttempts = maxAttempts;
            this.windowSeconds = windowSeconds;
        }

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }

        public RateLimitRule toRule(String name) {
            return new RateLimitRule(name, maxAttempts, windowSeconds);
        }
    }
}
