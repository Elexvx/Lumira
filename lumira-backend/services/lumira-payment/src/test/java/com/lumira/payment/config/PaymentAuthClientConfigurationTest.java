package com.lumira.payment.config;

import com.lumira.api.client.AuthInternalApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Iterator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentAuthClientConfigurationTest {

    @Test
    void authInternalApiSendsInternalTokenHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AuthInternalApi api = new PaymentAuthClientConfiguration().authInternalApi(
                "http://auth-service:8080",
                "strong-internal-token-2026",
                provider(builder)
        );
        server.expect(requestTo("http://auth-service:8080/internal/auth/sessions/session-1/current-user"))
                .andExpect(header("X-Job-Token", "strong-internal-token-2026"))
                .andRespond(withSuccess("{\"userId\":42,\"username\":\"alice\"}", MediaType.APPLICATION_JSON));

        var currentUser = api.currentUser("session-1");

        assertThat(currentUser.userId()).isEqualTo(42L);
        assertThat(currentUser.username()).isEqualTo("alice");
        server.verify();
    }

    private static ObjectProvider<RestClient.Builder> provider(RestClient.Builder builder) {
        return new ObjectProvider<>() {
            @Override
            public RestClient.Builder getObject(Object... args) {
                return builder;
            }

            @Override
            public RestClient.Builder getIfAvailable() {
                return builder;
            }

            @Override
            public RestClient.Builder getIfUnique() {
                return builder;
            }

            @Override
            public RestClient.Builder getObject() {
                return builder;
            }

            @Override
            public Iterator<RestClient.Builder> iterator() {
                return Stream.of(builder).iterator();
            }
        };
    }
}
