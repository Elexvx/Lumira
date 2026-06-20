package com.lumira.saas.modules.ai.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AiNativeToolRuntimeVisibilityTest {

    @Test
    void regularUserOnlySeesRbacAllowedReadOnlyLowTools() {
        DefaultAiNativeToolRuntimeService service = service(request -> AuthorizationDecision.deny("UNUSED", "unused"));

        List<String> visible = service.listTools(new CurrentUser(100L, "reader", 1001L, "s1", 1, true, Set.of()))
                .stream()
                .map(AiVO.ToolVO::getToolCode)
                .toList();

        assertThat(visible)
                .contains("system.permission.snapshot")
                .doesNotContain("system.user.search", "system.user.create", "system.config.update");
    }

    @Test
    void employeeVisibilityUsesAuthorizationServicePerTool() {
        DefaultAiNativeToolRuntimeService service = service(request -> {
            if ("system.permission.snapshot".equals(request.toolCode())) {
                assertThat(request.actionCode()).isEqualTo("view");
                return AuthorizationDecision.allow("VIEW_GRANT", "view");
            }
            if ("system.user.create".equals(request.toolCode())) {
                assertThat(request.actionCode()).isEqualTo("execute");
                return AuthorizationDecision.requireConfirm("HIGH_CONFIRM", "confirm", List.of("HIGH_CONFIRM"));
            }
            if ("system.config.update".equals(request.toolCode())) {
                return AuthorizationDecision.requireApproval("CRITICAL_APPROVAL", "approval", List.of("CRITICAL_APPROVAL"));
            }
            return AuthorizationDecision.deny("DENY", "deny");
        });

        List<String> visible = service.listTools(currentUser(), 300L)
                .stream()
                .map(AiVO.ToolVO::getToolCode)
                .toList();

        assertThat(visible)
                .contains("system.permission.snapshot", "system.user.create")
                .doesNotContain("system.config.update", "system.user.delete");
    }

    @Test
    void executeStillPerformsSecondAuthorizationCheck() {
        DefaultAiNativeToolRuntimeService service = service(request -> AuthorizationDecision.deny("EXECUTE_DENIED", "execute denied"));
        AiDTO.ToolExecuteRequest executeRequest = new AiDTO.ToolExecuteRequest();
        executeRequest.setEmployeeId(300L);
        executeRequest.setToolCode("system.permission.snapshot");
        executeRequest.setArguments(Map.of());
        executeRequest.setConfirmed(true);

        assertThatThrownBy(() -> service.execute(currentUser(), executeRequest))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("execute denied");
    }

    private DefaultAiNativeToolRuntimeService service(Function<AuthorizationRequest, AuthorizationDecision> evaluator) {
        return new DefaultAiNativeToolRuntimeService(
                new StubQueryOperations(),
                new PermissionGuard(),
                authorization(evaluator),
                mock(AiSkillPermissionChecker.class),
                new ObjectMapper(),
                mock(AiPlatformQueryFacade.class),
                mock(AiIamQueryFacade.class),
                null,
                mock(FileInternalApi.class)
        );
    }

    private AuthorizationService authorization(Function<AuthorizationRequest, AuthorizationDecision> evaluator) {
        return new AuthorizationService() {
            @Override
            public AuthorizationDecision evaluate(AuthorizationRequest request) {
                return evaluator.apply(request);
            }

            @Override
            public void require(AuthorizationRequest request) {
                AuthorizationDecision decision = evaluate(request);
                if (!decision.allowed()) {
                    throw new BizException(ErrorCode.FORBIDDEN, decision.message());
                }
            }
        };
    }

    private CurrentUser currentUser() {
        return new CurrentUser(
                100L,
                "admin",
                1001L,
                "session-1",
                1,
                true,
                Set.of("*")
        );
    }

    private static class StubQueryOperations extends MyBatisQueryOperations {
        @Override
        public boolean exists(String sql, Object... args) {
            return sql.contains("from ai_employee") || super.exists(sql, args);
        }

        @Override
        public int update(String sql, Object... args) {
            return 1;
        }
    }
}
