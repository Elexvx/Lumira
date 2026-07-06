package com.lumira.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TrustedServiceBaseUrlValidatorTest {

    @Test
    void acceptsHttpAndHttpsBaseUrls() {
        assertThat(TrustedServiceBaseUrlValidator.requireHttpBaseUrl("http://service-owner:8080", "service.base-url"))
                .isEqualTo("http://service-owner:8080");
        assertThat(TrustedServiceBaseUrlValidator.requireHttpBaseUrl("https://service-owner/internal", "service.base-url"))
                .isEqualTo("https://service-owner/internal");
    }

    @Test
    void rejectsUntrustedBaseUrlForms() {
        assertThatThrownBy(() -> TrustedServiceBaseUrlValidator.requireHttpBaseUrl("ftp://service-owner", "service.base-url"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must use http or https");
        assertThatThrownBy(() -> TrustedServiceBaseUrlValidator.requireHttpBaseUrl("http://token@service-owner", "service.base-url"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not include user info");
        assertThatThrownBy(() -> TrustedServiceBaseUrlValidator.requireHttpBaseUrl("http://service-owner?trace=1", "service.base-url"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not include query or fragment");
        assertThatThrownBy(() -> TrustedServiceBaseUrlValidator.requireHttpBaseUrl("http://service-owner#fragment", "service.base-url"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not include query or fragment");
    }
}
