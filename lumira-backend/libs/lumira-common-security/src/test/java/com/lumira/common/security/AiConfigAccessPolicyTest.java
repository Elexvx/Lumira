package com.lumira.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigAccessPolicyTest {

    @Test
    void manageableConfigKeyRequiresAiSafePrefix() {
        assertThat(AiConfigAccessPolicy.isAiManageableConfigKey("branding.website-name")).isTrue();
        assertThat(AiConfigAccessPolicy.isAiManageableConfigKey(" agreement.user-agreement-markdown ")).isTrue();
        assertThat(AiConfigAccessPolicy.isAiManageableConfigKey("auth.default-registration-role-code")).isFalse();
    }

    @Test
    void manageableConfigKeyRejectsSensitiveLookingKeys() {
        assertThat(AiConfigAccessPolicy.isAiManageableConfigKey("branding.api-secret")).isFalse();
        assertThat(AiConfigAccessPolicy.isAiManageableConfigKey("watermark.private-note")).isFalse();
    }

    @Test
    void looksSensitiveDetectsSecretLikeValues() {
        assertThat(AiConfigAccessPolicy.looksSensitive("jwt.secret")).isTrue();
        assertThat(AiConfigAccessPolicy.looksSensitive("notification.wechat-official.app-secret")).isTrue();
        assertThat(AiConfigAccessPolicy.looksSensitive("branding.website-name")).isFalse();
        assertThat(AiConfigAccessPolicy.looksSensitive("   ")).isFalse();
    }
}
