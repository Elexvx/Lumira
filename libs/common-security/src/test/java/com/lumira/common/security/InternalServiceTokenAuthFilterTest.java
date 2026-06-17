package com.lumira.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceTokenAuthFilterTest {

    @Test
    void recognizesRootAndContextPrefixedInternalPaths() {
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/internal/jobs/outbox/relay")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/message/internal/jobs/outbox/relay")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/file/internal/jobs/processing/run")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/payment/internal/jobs/outbox/relay")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/plugin/internal/jobs/outbox/relay")).isTrue();
    }

    @Test
    void ignoresPublicAndApplicationPaths() {
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath(null)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/api/v2/platform/public/bootstrap")).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/message/api/v2/notices")).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/settings/internalization")).isFalse();
    }

    @Test
    void authorizesOnlyExactInternalToken() {
        String internalToken = "strong-internal-service-token-2026";

        assertThat(InternalServiceTokenAuthFilter.isAuthorized(internalToken, internalToken)).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized(null, internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized("   ", internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized("strong-internal-service-token-202X", internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized(internalToken + " ", internalToken)).isFalse();
    }
}
