package com.lumira.alerting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.exception.BizException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertingSecretCryptoTest {
    private static final String MASTER_KEY = "test-only-alerting-master-key-32-characters";

    @Test
    void encryptsWithRandomIvAndMasksSecrets() {
        AlertingSecretCrypto crypto = new AlertingSecretCrypto(new ObjectMapper(), MASTER_KEY);
        Map<String, Object> config = Map.of(
                "appId", "app-1",
                "appSecret", "secret-value",
                "clientSecret", "dingtalk-secret"
        );

        String first = crypto.encrypt(config);
        String second = crypto.encrypt(config);

        assertThat(first).startsWith("a1:").isNotEqualTo(second);
        assertThat(crypto.decrypt(first)).containsAllEntriesOf(config);
        assertThat(crypto.masked(config))
                .containsEntry("appSecret", "******")
                .containsEntry("clientSecret", "******")
                .containsEntry("appId", "app-1");
    }

    @Test
    void retainsOnlyOmittedOrMaskedSecretFieldsOnUpdate() {
        AlertingSecretCrypto crypto = new AlertingSecretCrypto(new ObjectMapper(), MASTER_KEY);

        Map<String, Object> merged = crypto.retainExistingSecrets(
                Map.of("appId", "old-app", "appSecret", "old-secret", "clientSecret", "old-client", "signSecret", "old-sign"),
                Map.of("appId", "new-app", "appSecret", "******", "clientSecret", "******")
        );

        assertThat(merged)
                .containsEntry("appId", "new-app")
                .containsEntry("appSecret", "old-secret")
                .containsEntry("clientSecret", "old-client")
                .containsEntry("signSecret", "old-sign");
    }

    @Test
    void failsClosedWhenMasterKeyIsMissing() {
        AlertingSecretCrypto crypto = new AlertingSecretCrypto(new ObjectMapper(), "");

        assertThatThrownBy(() -> crypto.encrypt(Map.of("secret", "value")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("ALERTING_SECRETS_MASTER_KEY");
    }
}
