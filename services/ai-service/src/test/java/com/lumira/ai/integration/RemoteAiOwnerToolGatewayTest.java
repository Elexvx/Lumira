package com.lumira.ai.integration;

import com.lumira.ai.config.AiOwnerIntegrationProperties;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteAiOwnerToolGatewayTest {

    @Test
    void permissionSnapshotFallsBackWhenIamOwnerIsNotConfigured() {
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(
                new AiOwnerIntegrationProperties(),
                RestClient.builder()
        );

        var execution = gateway.execute(
                user(),
                new AiToolVO("system.permission.snapshot", "权限快照", "system", "读取权限快照", "MEDIUM", true, true, "system.permission.snapshot", Map.of()),
                Map.of()
        );

        assertThat(execution.remote()).isFalse();
        assertThat(execution.degraded()).isTrue();
        assertThat(execution.data()).containsEntry("tenantId", 1001L);
        assertThat(execution.data()).containsEntry("degradedReason", "iam-owner-not-configured");
        assertThat(gateway.degradedOwners()).containsExactly("iam", "platform", "file");
    }

    private CurrentUser user() {
        return new CurrentUser(7L, "ai-user", 1001L, "s1", 1, true, Set.of("*"));
    }
}
