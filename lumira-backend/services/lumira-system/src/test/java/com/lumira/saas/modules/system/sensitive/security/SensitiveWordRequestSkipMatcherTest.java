package com.lumira.saas.modules.system.sensitive.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveWordRequestSkipMatcherTest {

    @Test
    void shouldSkipSensitiveWordManagementPathsWithOrWithoutContextPath() {
        assertThat(SensitiveWordRequestSkipMatcher.shouldSkipPath("/api/v1/sensitive-words")).isTrue();
        assertThat(SensitiveWordRequestSkipMatcher.shouldSkipPath("/api/v1/sensitive-words/1")).isTrue();
        assertThat(SensitiveWordRequestSkipMatcher.shouldSkipPath("/lumira/api/v1/sensitive-words")).isTrue();
        assertThat(SensitiveWordRequestSkipMatcher.shouldSkipPath("/lumira/api/v1/sensitive-words/check")).isTrue();
    }

    @Test
    void shouldNotSkipBusinessPayloadPathsOrPartialPrefixMatches() {
        assertThat(SensitiveWordRequestSkipMatcher.shouldSkipPath("/api/v1/work-orders")).isFalse();
        assertThat(SensitiveWordRequestSkipMatcher.shouldSkipPath("/api/v1/sensitive-words-extra")).isFalse();
        assertThat(SensitiveWordRequestSkipMatcher.shouldSkipPath("/api/v1/plugin-settings")).isFalse();
    }
}
