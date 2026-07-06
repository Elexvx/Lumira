package com.lumira.common.web.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lumira.common.web.WebProperties;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityRateLimitFilterTest {

    @Test
    void authenticationAttemptPathsShouldShareLoginRateLimit() throws Exception {
        SecurityRateLimitProperties properties = new SecurityRateLimitProperties();
        properties.setLogin(new SecurityRateLimitProperties.Rule(1, 60));
        SecurityRateLimitFilter filter = filter(properties);

        for (String path : List.of(
                "/api/v1/auth/login/code/challenge",
                "/api/v1/auth/login/code/complete",
                "/api/v1/auth/second-factor/complete",
                "/api/v1/auth/passkeys/authentication/options",
                "/api/v1/auth/passkeys/authentication/complete",
                "/api/v1/auth/wechat/login"
        )) {
            MockHttpServletResponse first = new MockHttpServletResponse();
            filter.doFilterInternal(request(path), first, okChain());
            MockHttpServletResponse second = new MockHttpServletResponse();
            filter.doFilterInternal(request(path), second, okChain());

            assertThat(first.getStatus()).isEqualTo(200);
            assertThat(second.getStatus()).isEqualTo(429);
        }
    }

    private SecurityRateLimitFilter filter(SecurityRateLimitProperties properties) {
        WebProperties webProperties = new WebProperties();
        return new SecurityRateLimitFilter(
                properties,
                new RateLimitService(provider(null)),
                provider(new ClientIpResolver(webProperties)),
                provider(null),
                provider(new ObjectMapper().registerModule(new JavaTimeModule()))
        );
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.10");
        return request;
    }

    private FilterChain okChain() {
        return (request, response) -> {
        };
    }

    private <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}
