package com.lumira.saas.modules.ai.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiEmployeeRuntimeServiceTest {

    @Test
    void recordsFailureAuditWhenModelCallFails() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiChatModelFactory.AiChatClient chatClient = mock(AiChatModelFactory.AiChatClient.class);
        AiLlmServiceConfigProvider configProvider = mock(AiLlmServiceConfigProvider.class);
        AiToolRegistry toolRegistry = mock(AiToolRegistry.class);
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        AiKnowledgeBaseAppService knowledgeBaseAppService = mock(AiKnowledgeBaseAppService.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                toolRegistry,
                permissionChecker,
                knowledgeBaseAppService
        );
        AiDTO.ChatRequest request = chatRequest(List.of("customer.reply"));

        when(conversationService.ensureConversation(anyLong(), anyLong(), anyLong(), isNull(), any())).thenReturn(10L);
        when(conversationService.recordMessage(anyLong(), eq(10L), eq("USER"), any())).thenReturn(100L);
        when(configProvider.findById(anyLong(), isNull())).thenReturn(Optional.empty());
        when(configProvider.findDefaultForEmployee(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(toolRegistry.listRegisteredSkills(anyLong(), anyLong())).thenReturn(List.of());
        when(chatModelFactory.create(nullable(AiLlmServiceConfig.class))).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenThrow(new BizException(ErrorCode.BIZ_ERROR, "LLM 调用失败: timeout"));

        assertThatThrownBy(() -> service.chat(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessage("LLM 调用失败: timeout");

        assertThat(jdbcTemplate.lastUpdateSql).contains("insert into ai_tool_audit_log");
        assertThat(jdbcTemplate.lastUpdateArgs[1]).isEqualTo(10L);
        assertThat(jdbcTemplate.lastUpdateArgs[2]).isEqualTo(1L);
        assertThat(jdbcTemplate.lastUpdateArgs[3]).isEqualTo("customer.reply");
        assertThat(jdbcTemplate.lastUpdateArgs[5]).isEqualTo("allow");
        assertThat(jdbcTemplate.lastUpdateArgs[8]).isEqualTo("FAIL");
        assertThat(jdbcTemplate.lastUpdateArgs[9]).isEqualTo("LLM 调用失败: timeout");
        assertThat(jdbcTemplate.lastUpdateArgs[11].toString()).contains("\"code\":\"B0001\"");
    }

    @Test
    void recordsFailureAuditWhenSkillPermissionDeniedBeforeConversationCreated() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                conversationService,
                mock(AiToolRegistry.class),
                permissionChecker,
                mock(AiKnowledgeBaseAppService.class)
        );
        AiDTO.ChatRequest request = chatRequest(List.of("data.export"));

        doThrow(new BizException(ErrorCode.FORBIDDEN, "技能已被禁用: data.export"))
                .when(permissionChecker)
                .verifyAllowed(anyLong(), anyLong(), eq(List.of("data.export")), eq(false));

        assertThatThrownBy(() -> service.chat(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessage("技能已被禁用: data.export");

        assertThat(jdbcTemplate.lastUpdateSql).contains("insert into ai_tool_audit_log");
        assertThat(jdbcTemplate.lastUpdateArgs[1]).isNull();
        assertThat(jdbcTemplate.lastUpdateArgs[2]).isEqualTo(1L);
        assertThat(jdbcTemplate.lastUpdateArgs[3]).isEqualTo("data.export");
        assertThat(jdbcTemplate.lastUpdateArgs[5]).isEqualTo("deny");
        assertThat(jdbcTemplate.lastUpdateArgs[8]).isEqualTo("FAIL");
        assertThat(jdbcTemplate.lastUpdateArgs[9]).isEqualTo("技能已被禁用: data.export");
    }

    @Test
    void autoExecutesReadOnlyUserSearchTool() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        AiToolOrchestrationService orchestrationService = mock(AiToolOrchestrationService.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class),
                orchestrationService
        );
        AiDTO.ChatRequest request = chatRequest(List.of());
        request.setMessage("查看系统有几个用户？");
        AiVO.ToolPlanVO plan = new AiVO.ToolPlanVO();
        plan.setId(9L);
        plan.setStatus("PENDING");
        plan.setRequiresConfirm(false);
        plan.setToolCode("system.user.search");
        AiVO.ToolExecuteResultVO toolResult = new AiVO.ToolExecuteResultVO();
        toolResult.setToolCode("system.user.search");
        toolResult.setResultStatus("SUCCESS");
        toolResult.setMessage("工具调用成功");
        toolResult.setData(Map.of("total", 3L, "count", 1, "limit", 1));

        when(conversationService.ensureConversation(anyLong(), anyLong(), anyLong(), isNull(), any())).thenReturn(10L);
        when(conversationService.recordMessage(anyLong(), eq(10L), eq("USER"), any())).thenReturn(100L);
        when(orchestrationService.tryPropose(any(), any())).thenReturn(Optional.of(plan));
        when(orchestrationService.confirm(any(), any())).thenReturn(toolResult);

        AiVO.ChatResponseVO response = service.chat(currentUser(), request);

        assertThat(response.getReplyText()).contains("共有 3 个系统用户");
        assertThat(response.getToolResult()).isEqualTo(toolResult);
        assertThat(response.getToolPlan()).isEqualTo(plan);
    }

    private DefaultAiEmployeeRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiLlmServiceConfigProvider configProvider,
            AiChatModelFactory chatModelFactory,
            AiConversationService conversationService,
            AiToolRegistry toolRegistry,
            AiSkillPermissionChecker permissionChecker,
            AiKnowledgeBaseAppService knowledgeBaseAppService
    ) {
        return newService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                toolRegistry,
                permissionChecker,
                knowledgeBaseAppService,
                null
        );
    }

    private DefaultAiEmployeeRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiLlmServiceConfigProvider configProvider,
            AiChatModelFactory chatModelFactory,
            AiConversationService conversationService,
            AiToolRegistry toolRegistry,
            AiSkillPermissionChecker permissionChecker,
            AiKnowledgeBaseAppService knowledgeBaseAppService,
            AiToolOrchestrationService orchestrationService
    ) {
        return new DefaultAiEmployeeRuntimeService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                toolRegistry,
                permissionChecker,
                knowledgeBaseAppService,
                orchestrationService
        );
    }

    private CurrentUser currentUser() {
        return new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, Set.of("ai:chat:send"));
    }

    private AiDTO.ChatRequest chatRequest(List<String> skillCodes) {
        AiDTO.ChatRequest request = new AiDTO.ChatRequest();
        request.setEmployeeId(1L);
        request.setMessage("hello");
        request.setSkillCodes(skillCodes);
        return request;
    }

    private static class StubQueryOperations extends MyBatisQueryOperations {
        private String lastUpdateSql;
        private Object[] lastUpdateArgs;

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            this.lastUpdateArgs = args;
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from ai_employee e")) {
                AiVO.EmployeeDetailVO employee = new AiVO.EmployeeDetailVO();
                employee.setId(1L);
                employee.setEnabled(true);
                return List.of((T) employee);
            }
            return List.of();
        }
    }
}
