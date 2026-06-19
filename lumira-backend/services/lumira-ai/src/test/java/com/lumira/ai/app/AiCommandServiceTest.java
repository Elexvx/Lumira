package com.lumira.ai.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.ai.dto.AiCommandModels.KnowledgeSearchRequest;
import com.lumira.ai.integration.AiOwnerToolGateway;
import com.lumira.ai.provider.AiProviderRuntime;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiCommandServiceTest {

    @Test
    void searchKnowledgeBoundsLimitAndReturnsRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService readQueryService = mock(AiReadQueryService.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        AiCommandService service = new AiCommandService(jdbcTemplate, readQueryService, noOpGateway(), noOpProvider(), new ObjectMapper(), new PermissionGuard());

        var references = service.searchKnowledge(user(), new KnowledgeSearchRequest("policy", List.of(1L, 2L), 500));

        assertThat(references).isEmpty();
    }

    @Test
    void executeLocalPermissionSnapshotTool() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiCommandService service = new AiCommandService(jdbcTemplate, new AiReadQueryService(jdbcTemplate), noOpGateway(), noOpProvider(), new ObjectMapper(), new PermissionGuard());

        var result = service.executeTool(user(), new com.lumira.ai.dto.AiCommandModels.ToolExecuteRequest(
                1L,
                null,
                "system.permission.snapshot",
                java.util.Map.of(),
                true
        ));

        assertThat(result.resultStatus()).isEqualTo("SUCCESS");
        assertThat(result.data()).containsEntry("tenantId", 1001L);
        assertThat(result.data()).containsEntry("remoteOwnerCall", false);
        assertThat(result.data()).containsEntry("degraded", false);
    }

    @Test
    void executeToolShouldRequireDeclaredToolPermission() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiCommandService service = new AiCommandService(jdbcTemplate, new AiReadQueryService(jdbcTemplate), noOpGateway(), noOpProvider(), new ObjectMapper(), new PermissionGuard());

        assertThrows(com.lumira.common.exception.BizException.class, () -> service.executeTool(
                userWithPermissions("ai:tool:execute"),
                new com.lumira.ai.dto.AiCommandModels.ToolExecuteRequest(
                        1L,
                        null,
                        "system.config.read",
                        java.util.Map.of("keys", List.of("security.captcha-enabled")),
                        true
                )
        ));
    }

    private AiOwnerToolGateway noOpGateway() {
        return new AiOwnerToolGateway() {
            @Override
            public ToolExecution execute(CurrentUser currentUser, AiToolVO tool, Map<String, Object> arguments) {
                return new ToolExecution(Map.of(
                        "userId", currentUser.getUserId(),
                        "tenantId", currentUser.getCurrentTenantId(),
                        "username", currentUser.getUsername(),
                        "permissions", currentUser.getPermissions()
                ), false, false);
            }

            @Override
            public List<String> configuredOwners() {
                return List.of();
            }

            @Override
            public List<String> degradedOwners() {
                return List.of("iam", "platform", "file");
            }
        };
    }

    private AiProviderRuntime noOpProvider() {
        return new AiProviderRuntime() {
            @Override
            public ChatCompletion complete(ChatPrompt prompt) {
                return new ChatCompletion("ok", "test-provider", "test-chat", false, false);
            }

            @Override
            public EmbeddingVector embed(String text) {
                return new EmbeddingVector("test-embedding", List.of(1.0d, 0.0d), false, false);
            }

            @Override
            public ProviderStatus status() {
                return new ProviderStatus("test-provider", "test-chat", "test-embedding", false, false);
            }
        };
    }

    private CurrentUser user() {
        return new CurrentUser(7L, "ai-user", 1001L, "s1", 1, true, Set.of("*"));
    }

    private CurrentUser userWithPermissions(String... permissions) {
        return new CurrentUser(7L, "ai-user", 1001L, "s1", 1, true, Set.of(permissions));
    }
}
