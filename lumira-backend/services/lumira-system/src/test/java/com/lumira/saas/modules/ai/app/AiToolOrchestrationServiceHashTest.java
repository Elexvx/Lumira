package com.lumira.saas.modules.ai.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiToolOrchestrationServiceHashTest {

    @Test
    void proposeWritesArgumentsHashAndAuthorizationSnapshot() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));

        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin", "limit", 10)));

        assertThat(plan.getArgumentsHash()).hasSize(64);
        assertThat(plan.getAuthorizationSnapshotJson())
                .contains("\"toolCode\":\"system.user.search\"")
                .contains("\"authorizationVerdict\":\"ALLOW\"");
        assertThat(jdbc.insertSql).contains("arguments_hash", "authorization_snapshot_json", "approval_required");
        assertThat(jdbc.insertArgs[16]).isEqualTo(plan.getArgumentsHash());
        assertThat(jdbc.insertArgs[17]).isEqualTo(plan.getAuthorizationSnapshotJson());
        assertThat(jdbc.insertArgs[18]).isEqualTo(0);
    }

    @Test
    void confirmAllowsMatchingHashAndClaimsPendingPlanOnce() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));

        service.confirm(currentUser(), confirm(plan.getId()));

        assertThat(jdbc.executingClaims).isEqualTo(1);
        assertThat(jdbc.finalStatuses).contains("EXECUTED");
    }

    @Test
    void confirmRejectsTamperedArgumentsJson() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));
        plan.setArguments(Map.of("keyword", "tampered"));

        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("arguments were modified");
        assertThat(jdbc.finalStatuses).contains("BLOCKED");
    }

    @Test
    void confirmRejectsWhenAuthorizationChanges() {
        StubQueryOperations jdbc = new StubQueryOperations();
        MutableAuthorizationService authorization = new MutableAuthorizationService(request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        DefaultAiToolOrchestrationService service = service(jdbc, authorization);
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));
        authorization.evaluator = request -> AuthorizationDecision.deny("AUTHZ_DENY", "denied after propose");

        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("denied after propose");
        assertThat(jdbc.finalStatuses).contains("BLOCKED");
    }

    @Test
    void confirmRejectsDuplicatePendingClaim() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.claimUpdates = 0;
        DefaultAiToolOrchestrationService service = service(jdbc, request -> AuthorizationDecision.allow("AUTHZ_ALLOW", "allow"));
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));

        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class);
        assertThat(jdbc.executingClaims).isZero();
    }

    @Test
    void confirmRejectsWhenApprovalRequiredButNotGranted() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultAiToolOrchestrationService service = service(jdbc,
                request -> !"AI_AGENT".equals(request.channel())
                        ? AuthorizationDecision.allow("RBAC_ALLOW", "rbac allow")
                        : request.approvalGranted()
                        ? AuthorizationDecision.allow("AUTHZ_APPROVED", "approved")
                        : AuthorizationDecision.requireApproval("AUTHZ_APPROVAL", "approval required", List.of("AUTHZ_APPROVAL")));
        AiVO.ToolPlanVO plan = service.propose(currentUser(), propose(Map.of("keyword", "admin")));

        assertThat(plan.getApprovalRequired()).isTrue();
        assertThatThrownBy(() -> service.confirm(currentUser(), confirm(plan.getId())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("approval required");
    }

    private DefaultAiToolOrchestrationService service(StubQueryOperations jdbc, Function<AuthorizationRequest, AuthorizationDecision> evaluator) {
        return service(jdbc, new MutableAuthorizationService(evaluator));
    }

    private DefaultAiToolOrchestrationService service(StubQueryOperations jdbc, AuthorizationService authorizationService) {
        AiNativeToolRuntimeService runtimeService = mock(AiNativeToolRuntimeService.class);
        when(runtimeService.listTools(any(), anyLong())).thenReturn(List.of(tool()));
        AiVO.ToolExecuteResultVO executeResult = new AiVO.ToolExecuteResultVO();
        executeResult.setToolCode("system.user.search");
        executeResult.setResultStatus("SUCCESS");
        when(runtimeService.execute(any(), any())).thenReturn(executeResult);

        AiToolPolicyService policyService = mock(AiToolPolicyService.class);
        when(policyService.evaluate(anyLong(), anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new AiToolPolicyService.PolicyDecision("ALLOW", "policy allow", List.of("POLICY_ALLOW")));

        AiLlmServiceConfigProvider configProvider = mock(AiLlmServiceConfigProvider.class);
        when(configProvider.findSupervisor(anyLong())).thenReturn(Optional.empty());

        return new DefaultAiToolOrchestrationService(
                jdbc,
                new ObjectMapper(),
                runtimeService,
                policyService,
                configProvider,
                mock(AiChatModelFactory.class),
                new PermissionGuard(authorizationService),
                authorizationService
        );
    }

    private AiVO.ToolVO tool() {
        AiVO.ToolVO tool = new AiVO.ToolVO();
        tool.setToolCode("system.user.search");
        tool.setToolName("Search users");
        tool.setRiskLevel("LOW");
        tool.setReadOnly(true);
        tool.setNeedConfirm(false);
        tool.setRequiredPermission("system:user:view");
        return tool;
    }

    private AiDTO.ToolProposeRequest propose(Map<String, Object> arguments) {
        AiDTO.ToolProposeRequest request = new AiDTO.ToolProposeRequest();
        request.setEmployeeId(300L);
        request.setConversationId(900L);
        request.setToolCode("system.user.search");
        request.setMessage("search users");
        request.setArguments(arguments);
        return request;
    }

    private AiDTO.ToolConfirmRequest confirm(Long planId) {
        AiDTO.ToolConfirmRequest request = new AiDTO.ToolConfirmRequest();
        request.setPendingToolCallId(planId);
        return request;
    }

    private CurrentUser currentUser() {
        return new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, Set.of("*", "system:user:view"));
    }

    private static class MutableAuthorizationService implements AuthorizationService {
        private Function<AuthorizationRequest, AuthorizationDecision> evaluator;

        private MutableAuthorizationService(Function<AuthorizationRequest, AuthorizationDecision> evaluator) {
            this.evaluator = evaluator;
        }

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
    }

    private static class StubQueryOperations extends MyBatisQueryOperations {
        private AiVO.ToolPlanVO plan;
        private String insertSql;
        private Object[] insertArgs;
        private int claimUpdates = 1;
        private int executingClaims;
        private final List<String> finalStatuses = new java.util.ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("insert into ai_tool_call_plan")) {
                insertSql = sql;
                insertArgs = args;
                plan = new AiVO.ToolPlanVO();
                plan.setId(501L);
                plan.setTenantId((Long) args[0]);
                plan.setConversationId((Long) args[1]);
                plan.setEmployeeId((Long) args[2]);
                plan.setToolCode((String) args[4]);
                plan.setToolName((String) args[5]);
                plan.setActionType((String) args[6]);
                plan.setRiskLevel((String) args[7]);
                plan.setSummary((String) args[8]);
                plan.setPermissionKey((String) args[9]);
                plan.setRequiresConfirm(((Integer) args[10]) == 1);
                plan.setSupervisorVerdict((String) args[11]);
                plan.setSupervisorMessage((String) args[12]);
                plan.setPolicyVerdict((String) args[13]);
                plan.setPolicyMessage((String) args[14]);
                plan.setArguments(parseArguments((String) args[15]));
                plan.setArgumentsHash((String) args[16]);
                plan.setAuthorizationSnapshotJson((String) args[17]);
                plan.setApprovalRequired(((Integer) args[18]) == 1);
                plan.setStatus((String) args[19]);
                plan.setExpiresAt((LocalDateTime) args[20]);
                plan.setCreateTime((LocalDateTime) args[21]);
                return 1;
            }
            if (sql.contains("set status = 'EXECUTING'")) {
                if (claimUpdates == 1) {
                    executingClaims += 1;
                    plan.setStatus("EXECUTING");
                }
                return claimUpdates;
            }
            if (sql.contains("update ai_tool_call_plan") && sql.contains("set status = ?")) {
                finalStatuses.add((String) args[0]);
                plan.setStatus((String) args[0]);
                return 1;
            }
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("select id from ai_tool_call_plan") || sql.contains("last_insert_id")) {
                return requiredType.cast(501L);
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from ai_tool_call_plan")) {
                return plan == null ? List.of() : List.of((T) plan);
            }
            return List.of();
        }

        private Map<String, Object> parseArguments(String json) {
            try {
                return new ObjectMapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
            } catch (Exception exception) {
                return Map.of();
            }
        }
    }
}
