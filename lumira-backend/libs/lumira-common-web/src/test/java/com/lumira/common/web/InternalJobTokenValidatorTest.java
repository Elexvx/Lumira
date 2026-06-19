package com.lumira.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalJobTokenValidatorTest {

    @Test
    void shouldRequireConfiguredToken() {
        assertThat(InternalJobTokenValidator.isConfigured(null)).isFalse();
        assertThat(InternalJobTokenValidator.isConfigured("   ")).isFalse();
        assertThat(InternalJobTokenValidator.isConfigured("release-job-token-1234567890")).isTrue();
    }

    @Test
    void shouldAuthorizeOnlyExactToken() {
        String internalToken = "release-job-token-1234567890";

        assertThat(InternalJobTokenValidator.isAuthorized(internalToken, internalToken)).isTrue();
        assertThat(InternalJobTokenValidator.isAuthorized(null, internalToken)).isFalse();
        assertThat(InternalJobTokenValidator.isAuthorized("   ", internalToken)).isFalse();
        assertThat(InternalJobTokenValidator.isAuthorized("release-job-token-123456789X", internalToken)).isFalse();
        assertThat(InternalJobTokenValidator.isAuthorized(internalToken + " ", internalToken)).isFalse();
    }

    @Test
    void shouldRejectWhenInternalTokenIsMissing() {
        assertThat(InternalJobTokenValidator.isAuthorized("token", null)).isFalse();
        assertThat(InternalJobTokenValidator.isAuthorized("token", "  ")).isFalse();
    }
}
