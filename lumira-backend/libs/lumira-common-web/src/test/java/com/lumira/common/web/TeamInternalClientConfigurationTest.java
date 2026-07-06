package com.lumira.common.web;

import com.lumira.team.api.TeamInternalApi;
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

class TeamInternalClientConfigurationTest {

    @Test
    void teamInternalApiSendsScopedTeamToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TeamInternalApi api = teamInternalApi(builder, "team-token-2026");
        server.expect(requestTo("http://team-service:8087/internal/team/teams/21?requesterUserId=1001&requesterUserUuid=user-uuid-1001"))
                .andExpect(header("X-Job-Token", "team-token-2026"))
                .andRespond(withSuccess(
                        "{\"id\":21,\"teamCode\":\"TEAM-21\",\"teamName\":\"AI Team\",\"status\":\"ACTIVE\"}",
                        MediaType.APPLICATION_JSON
                ));

        var team = api.getTeam(1001L, "user-uuid-1001", 21L);

        assertThat(team.getId()).isEqualTo(21L);
        assertThat(team.getTeamName()).isEqualTo("AI Team");
        server.verify();
    }

    @Test
    void teamInternalApiReadsMembersWithScopedTeamToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TeamInternalApi api = teamInternalApi(builder, "team-token-2026");
        server.expect(requestTo("http://team-service:8087/internal/team/teams/21/members?requesterUserId=1001&requesterUserUuid=user-uuid-1001"))
                .andExpect(header("X-Job-Token", "team-token-2026"))
                .andRespond(withSuccess(
                        "[{\"id\":101,\"teamId\":21,\"userId\":1001,\"userUuid\":\"user-uuid-1001\",\"role\":\"OWNER\",\"status\":\"ACTIVE\"}]",
                        MediaType.APPLICATION_JSON
                ));

        var members = api.listActiveMembers(1001L, "user-uuid-1001", 21L);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getRole()).isEqualTo("OWNER");
        server.verify();
    }

    @Test
    void teamInternalApiRequiresTeamToken() {
        assertThatThrownBy(() -> teamInternalApi(RestClient.builder(), " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saas.internal.team-token is required");
    }

    @Test
    void teamInternalApiRejectsBaseUrlWithoutTrustedScheme() {
        assertThatThrownBy(() -> new TeamInternalClientConfiguration().teamInternalApi(
                "mailto:team-service",
                "team-token-2026",
                provider(RestClient.builder())
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saas.team.service-base-url")
                .hasMessageContaining("must use http or https");
    }

    private static TeamInternalApi teamInternalApi(RestClient.Builder builder, String teamToken) {
        return new TeamInternalClientConfiguration().teamInternalApi(
                "http://team-service:8087",
                teamToken,
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
