package com.lumira.common.web;

import com.lumira.api.client.AuthInternalApi;
import java.util.Iterator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthInternalClientConfigurationTest {

    @Test
    void authInternalApiUsesScopedAuthToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AuthInternalApi api = authInternalApi(builder, "auth-token-2026");
        server.expect(requestTo("http://auth-service:8082/internal/auth/sessions/session-1/current-user?expectedUserId=1001&expectedUserUuid=user-uuid-1001&expectedSessionVersion=7&expectedPermissionsVersion=perm-v7&expectedSimulatedRoleId=9"))
                .andExpect(header("X-Job-Token", "auth-token-2026"))
                .andRespond(withSuccess(
                        "{\"userId\":1001,\"username\":\"alice\",\"userUuid\":\"user-uuid-1001\",\"sessionId\":\"session-1\",\"sessionVersion\":7,\"authenticated\":true,\"permissions\":[\"message:read\"]}",
                        MediaType.APPLICATION_JSON
                ));

        var currentUser = api.currentUser("session-1", 1001L, "user-uuid-1001", 7, "perm-v7", 9L);

        assertThat(currentUser.userId()).isEqualTo(1001L);
        assertThat(currentUser.username()).isEqualTo("alice");
        server.verify();
    }

    @Test
    void authInternalApiRequiresAuthToken() {
        assertThatThrownBy(() -> authInternalApi(RestClient.builder(), " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saas.internal.auth-token is required");
    }

    @Test
    void authInternalApiRejectsUntrustedBaseUrl() {
        assertThatThrownBy(() -> new AuthInternalClientConfiguration().remoteAuthInternalApi(
                "http://token@auth-service:8082",
                "auth-token-2026",
                provider(RestClient.builder())
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saas.auth.service-base-url")
                .hasMessageContaining("must not include user info");
    }

    private static AuthInternalApi authInternalApi(RestClient.Builder builder, String authToken) {
        return new AuthInternalClientConfiguration().remoteAuthInternalApi(
                "http://auth-service:8082",
                authToken,
                provider(builder)
        );
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
