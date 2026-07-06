package com.lumira.common.web;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import java.util.Iterator;
import java.util.Map;
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

class SystemInternalClientConfigurationTest {

    @Test
    void systemInternalApiSendsAuthSystemTokenForTrustedProfileLookup() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SystemInternalApi api = systemInternalApi(builder, "message-token-2026");
        server.expect(requestTo("http://system-service:8081/internal/system/users/42/profile"))
                .andExpect(header("X-Job-Token", "auth-system-token-2026"))
                .andRespond(withSuccess(
                        "{\"userId\":42,\"userUuid\":\"user-uuid-42\",\"username\":\"alice\"}",
                        MediaType.APPLICATION_JSON
                ));

        var snapshot = api.findUserProfileById(42L);

        assertThat(snapshot.userId()).isEqualTo(42L);
        assertThat(snapshot.userUuid()).isEqualTo("user-uuid-42");
        server.verify();
    }

    @Test
    void systemInternalApiSendsMessageScopedTokenForSmtpRuntimeConfig() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SystemInternalApi api = systemInternalApi(builder, "message-token-2026");
        server.expect(requestTo("http://system-service:8081/internal/system/config/runtime/smtp"))
                .andExpect(header("X-Job-Token", "message-token-2026"))
                .andRespond(withSuccess(
                        "{\"smtp.host\":\"smtp.example.com\"}",
                        MediaType.APPLICATION_JSON
                ));

        Map<String, String> config = api.smtpRuntimeConfigValues();

        assertThat(config).containsEntry("smtp.host", "smtp.example.com");
        server.verify();
    }

    @Test
    void systemInternalApiSendsMessageScopedTokenForIdentityBatchLookup() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SystemInternalApi api = systemInternalApi(builder, "message-token-2026");
        server.expect(requestTo("http://system-service:8081/internal/system/users/identities-by-ids?ids=42&ids=43"))
                .andExpect(header("X-Job-Token", "message-token-2026"))
                .andRespond(withSuccess(
                        "[{\"userId\":42,\"userUuid\":\"user-uuid-42\",\"username\":\"alice\"}]",
                        MediaType.APPLICATION_JSON
                ));

        var users = api.userIdentitiesByIds(java.util.List.of(42L, 43L));

        assertThat(users).hasSize(1);
        assertThat(users.get(0).userUuid()).isEqualTo("user-uuid-42");
        server.verify();
    }

    @Test
    void systemInternalApiSendsMessageScopedTokenForRoleNameBatchLookup() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SystemInternalApi api = systemInternalApi(builder, "message-token-2026");
        server.expect(requestTo("http://system-service:8081/internal/system/roles/names-by-ids?ids=7"))
                .andExpect(header("X-Job-Token", "message-token-2026"))
                .andRespond(withSuccess(
                        "[{\"roleId\":7,\"roleName\":\"Admin\"}]",
                        MediaType.APPLICATION_JSON
                ));

        var roles = api.roleNamesByIds(java.util.List.of(7L));

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).roleName()).isEqualTo("Admin");
        assertThat(roles.get(0).roleCode()).isNull();
        server.verify();
    }

    @Test
    void systemInternalApiSendsMessageScopedTokenForRoleIdentityLookup() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SystemInternalApi api = systemInternalApi(builder, "message-token-2026");
        server.expect(requestTo("http://system-service:8081/internal/system/roles/7/identities"))
                .andExpect(header("X-Job-Token", "message-token-2026"))
                .andRespond(withSuccess(
                        "[{\"userId\":42,\"userUuid\":\"user-uuid-42\",\"username\":\"alice\"}]",
                        MediaType.APPLICATION_JSON
                ));

        var users = api.roleUserIdentities(7L);

        assertThat(users).hasSize(1);
        assertThat(users.get(0).userUuid()).isEqualTo("user-uuid-42");
        assertThat(users.get(0).email()).isNull();
        server.verify();
    }

    @Test
    void systemInternalApiUsesQueryAwareMessageTokenForUnreadReadModel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SystemInternalApi api = systemInternalApi(builder, "message-token-2026");
        server.expect(requestTo("http://system-service:8081/internal/system/read-model-version?contextName=message&scope=unread"))
                .andExpect(header("X-Job-Token", "message-token-2026"))
                .andRespond(withSuccess("7", MediaType.APPLICATION_JSON));

        Long version = api.readModelVersion("message", "unread");

        assertThat(version).isEqualTo(7L);
        server.verify();
    }

    @Test
    void systemInternalApiUsesAuthSystemTokenForPlatformBootstrapReadModel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SystemInternalApi api = systemInternalApi(builder, "message-token-2026");
        server.expect(requestTo("http://system-service:8081/internal/system/read-model-version?contextName=platform&scope=public-bootstrap"))
                .andExpect(header("X-Job-Token", "auth-system-token-2026"))
                .andRespond(withSuccess("11", MediaType.APPLICATION_JSON));

        Long version = api.readModelVersion("platform", "public-bootstrap");

        assertThat(version).isEqualTo(11L);
        server.verify();
    }

    @Test
    void systemInternalApiSendsMessageScopedTokenForOperationAudit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SystemInternalApi api = systemInternalApi(builder, "message-token-2026");
        server.expect(requestTo("http://system-service:8081/internal/system/audit/operation"))
                .andExpect(header("X-Job-Token", "message-token-2026"))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));

        Boolean recorded = api.recordOperationAudit(new OperationAuditRecordRequestDTO(
                42L,
                "user-uuid-42",
                "alice",
                "message",
                "send",
                "CREATE",
                "SUCCESS",
                "queued"
        ));

        assertThat(recorded).isTrue();
        server.verify();
    }

    @Test
    void systemInternalApiSendsPluginTokenForPluginOwnedBuiltinMenus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SystemInternalApi api = systemInternalApi(builder, "message-token-2026");
        server.expect(requestTo("http://system-service:8081/internal/system/menus/builtin"))
                .andExpect(header("X-Job-Token", "plugin-token-2026"))
                .andRespond(withSuccess(
                        "[{\"id\":1,\"name\":\"Dashboard\",\"children\":[]}]",
                        MediaType.APPLICATION_JSON
                ));

        var menus = api.builtinMenus();

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getName()).isEqualTo("Dashboard");
        server.verify();
    }

    @Test
    void systemInternalApiRejectsMissingScopedTokenForInvokedPath() {
        RestClient.Builder builder = RestClient.builder();
        SystemInternalApi api = systemInternalApi(builder, " ");

        assertThatThrownBy(api::smtpRuntimeConfigValues)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/internal/system/config/runtime/smtp");
    }

    @Test
    void systemInternalApiRejectsBaseUrlWithFragment() {
        assertThatThrownBy(() -> new SystemInternalClientConfiguration().remoteSystemInternalApi(
                "http://system-service:8081#fragment",
                "system-token-2026",
                "auth-token-2026",
                "auth-system-token-2026",
                "file-token-2026",
                "message-token-2026",
                "payment-token-2026",
                "plugin-token-2026",
                "job-token-2026",
                provider(RestClient.builder())
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saas.system.service-base-url")
                .hasMessageContaining("must not include query or fragment");
    }

    private static SystemInternalApi systemInternalApi(RestClient.Builder builder, String messageToken) {
        return new SystemInternalClientConfiguration().remoteSystemInternalApi(
                "http://system-service:8081",
                "system-token-2026",
                "auth-token-2026",
                "auth-system-token-2026",
                "file-token-2026",
                messageToken,
                "payment-token-2026",
                "plugin-token-2026",
                "job-token-2026",
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
