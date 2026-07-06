package com.lumira.job;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackendJobClientTest {

    @Test
    void constructorAcceptsTrustedInternalHttpTargets() {
        JobExecutorProperties properties = baseProperties();

        assertThatCode(() -> new BackendJobClient(properties)).doesNotThrowAnyException();
    }

    @Test
    void relayOutboxRejectsSystemTokenWhenDedicatedJobTokenMissing() {
        JobExecutorProperties properties = baseProperties();
        properties.getInternal().setJobToken(null);
        BackendJobClient client = new BackendJobClient(properties);

        assertThatThrownBy(client::relayOutbox)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Scoped internal job token is not configured");
    }

    @Test
    void constructorRejectsBaseUrlWithUserInfoBeforeTokenCanBeSent() {
        JobExecutorProperties properties = baseProperties();
        properties.setMessageServiceBaseUrl("https://token@example.com");

        assertThatThrownBy(() -> new BackendJobClient(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("messageServiceBaseUrl must not include user info");
    }

    @Test
    void constructorRejectsBaseUrlWithQueryBeforeTokenCanBeSent() {
        JobExecutorProperties properties = baseProperties();
        properties.setBackendBaseUrl("http://127.0.0.1:8080?redirect=https://evil.example");

        assertThatThrownBy(() -> new BackendJobClient(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backendBaseUrl must not include query or fragment");
    }

    @Test
    void constructorRejectsMissingRequiredMessageTarget() {
        JobExecutorProperties properties = baseProperties();
        properties.setMessageServiceBaseUrl(null);

        assertThatThrownBy(() -> new BackendJobClient(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("messageServiceBaseUrl is not configured");
    }

    private JobExecutorProperties baseProperties() {
        JobExecutorProperties properties = new JobExecutorProperties();
        properties.setBackendBaseUrl("http://127.0.0.1:1");
        properties.setMessageServiceBaseUrl("http://127.0.0.1:2");
        properties.getInternal().setMessageToken("message-token");
        properties.getInternal().setFileToken("file-token");
        properties.getInternal().setPaymentToken("payment-token");
        properties.getInternal().setPluginToken("plugin-token");
        properties.getInternal().setJobToken("job-token");
        return properties;
    }
}
