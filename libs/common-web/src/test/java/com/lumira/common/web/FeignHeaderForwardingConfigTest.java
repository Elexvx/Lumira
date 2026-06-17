package com.lumira.common.web;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class FeignHeaderForwardingConfigTest {

    @Test
    void shouldInjectSingleJobTokenForInternalRequests() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig("release-job-token-1234567890").requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("POST");
        template.uri("/payment/internal/jobs/outbox/relay");
        template.header("X-Job-Token", "stale-token");

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-Job-Token");
        assertThat(values).containsExactly("release-job-token-1234567890");
    }

    @Test
    void shouldNotInjectJobTokenForPublicRequests() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig("release-job-token-1234567890").requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/api/v2/payment/providers");

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("X-Job-Token");
    }
}
