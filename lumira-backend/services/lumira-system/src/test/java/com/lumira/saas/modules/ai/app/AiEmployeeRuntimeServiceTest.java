package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiEmployeeRuntimeServiceTest {

    @Test
    void chatShouldRejectUnauthenticatedUserBeforeLoadingEmployeeOrConversation() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class)
        );

        assertThatThrownBy(() -> service.chat(unauthenticatedUser(), chatRequest(List.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.queryCalled).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verify(conversationService, never()).ensureConversation(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void chatShouldRejectMissingSessionVersionBeforeLoadingEmployeeOrConversation() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class)
        );

        assertThatThrownBy(() -> service.chat(missingSessionVersionUser(), chatRequest(List.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.queryCalled).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verify(conversationService, never()).ensureConversation(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void chatShouldRejectMissingUserUuidBeforeLoadingEmployeeOrConversation() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class)
        );
        CurrentUser currentUser = currentUser();
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> service.chat(currentUser, chatRequest(List.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.queryCalled).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verify(conversationService, never()).ensureConversation(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void chatShouldRejectMissingPermissionsVersionBeforeLoadingEmployeeOrConversation() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class)
        );
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion(" ");

        assertThatThrownBy(() -> service.chat(currentUser, chatRequest(List.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.queryCalled).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verify(conversationService, never()).ensureConversation(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void chatShouldRequireSendPermissionBeforeLoadingEmployeeOrConversation() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class)
        );

        assertThatThrownBy(() -> service.chat(currentUserWithoutChatPermission(), chatRequest(List.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.queryCalled).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verify(conversationService, never()).ensureConversation(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void chatShouldRequireLiveSendPermissionBeforeLoadingEmployeeOrConversation() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("perm-v2", Set.of("ai:view")));
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class),
                null,
                permissionSnapshotService
        );

        assertThatThrownBy(() -> service.chat(currentUser(), chatRequest(List.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.queryCalled).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verify(conversationService, never()).ensureConversation(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void chatShouldRejectRevokedSessionTicketBeforeLoadingEmployeeOrConversation() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket(any(), anyLong(), any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class),
                null,
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> service.chat(currentUser(), chatRequest(List.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(jdbcTemplate.queryCalled).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verify(conversationService, never()).ensureConversation(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void chatShouldRejectDisabledTrustedIdentityBeforeLoadingEmployeeOrConversation() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", "admin-live", "DISABLED"));
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                mock(AiLlmServiceConfigProvider.class),
                mock(AiChatModelFactory.class),
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class),
                null,
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> service.chat(currentUser(), chatRequest(List.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.queryCalled).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verify(conversationService, never()).ensureConversation(anyLong(), any(), anyLong(), any(), any());
        verify(permissionSnapshotService, never()).isTrustedActiveUser(100L, "user-uuid-100");
    }

    @Test
    void chatShouldRefreshLiveUsernameBeforeLoadingEmployeeOrConversation() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiChatModelFactory.AiChatClient chatClient = mock(AiChatModelFactory.AiChatClient.class);
        AiLlmServiceConfigProvider configProvider = mock(AiLlmServiceConfigProvider.class);
        AiToolRegistry toolRegistry = mock(AiToolRegistry.class);
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        AiKnowledgeBaseAppService knowledgeBaseAppService = mock(AiKnowledgeBaseAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", "admin-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("perm-v2", Set.of("ai:chat:send")));
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                toolRegistry,
                permissionChecker,
                knowledgeBaseAppService,
                null,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        AiDTO.ChatRequest request = new AiDTO.ChatRequest();
        request.setMessage("hello");
        AiVO.ChatResponseVO modelResponse = new AiVO.ChatResponseVO();
        modelResponse.setReplyText("OK");
        CurrentUser currentUser = currentUser();
        currentUser.setUsername("admin-stale");

        when(conversationService.ensureConversation(anyLong(), eq("user-uuid-100"), eq(1L), isNull(), any())).thenReturn(10L);
        when(conversationService.recordMessage(eq(100L), eq("user-uuid-100"), eq(10L), eq("USER"), any())).thenReturn(100L);
        when(configProvider.findDefault()).thenReturn(Optional.empty());
        when(chatModelFactory.create(nullable(AiLlmServiceConfig.class))).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenReturn(modelResponse);

        service.chat(currentUser, request);

        assertThat(currentUser.getUsername()).isEqualTo("admin-live");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("perm-v2");
    }

    @Test
    void defaultConversationUsesPersistedAssistantEmployeeId() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiChatModelFactory.AiChatClient chatClient = mock(AiChatModelFactory.AiChatClient.class);
        AiLlmServiceConfigProvider configProvider = mock(AiLlmServiceConfigProvider.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class)
        );
        AiDTO.ChatRequest request = new AiDTO.ChatRequest();
        request.setMessage("hello");
        AiVO.ChatResponseVO modelResponse = new AiVO.ChatResponseVO();
        modelResponse.setReplyText("OK");

        when(conversationService.ensureConversation(anyLong(), eq("user-uuid-100"), eq(1L), isNull(), any())).thenReturn(10L);
        when(conversationService.recordMessage(eq(100L), eq("user-uuid-100"), eq(10L), eq("USER"), any())).thenReturn(100L);
        when(configProvider.findDefault()).thenReturn(Optional.empty());
        when(chatModelFactory.create(nullable(AiLlmServiceConfig.class))).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenReturn(modelResponse);

        AiVO.ChatResponseVO response = service.chat(currentUser(), request);

        assertThat(response.getConversationId()).isEqualTo(10L);
        assertThat(jdbcTemplate.lastUpdateSql).contains("insert into ai_tool_audit_log");
        assertThat(jdbcTemplate.lastUpdateSql).contains("owner_user_id", "owner_user_uuid");
        assertThat(jdbcTemplate.lastUpdateArgs[2]).isEqualTo(100L);
        assertThat(jdbcTemplate.lastUpdateArgs[3]).isEqualTo("user-uuid-100");
        assertThat(jdbcTemplate.lastUpdateArgs[4]).isEqualTo("chat.general");
        assertThat(jdbcTemplate.lastUpdateArgs[7]).isEqualTo(0);
        assertThat(jdbcTemplate.lastUpdateArgs[9]).isEqualTo("SUCCESS");
        verify(conversationService).ensureConversation(eq(100L), eq("user-uuid-100"), eq(1L), isNull(), any());
    }

    @Test
    void chatShouldUseRefreshedTrustedUserIdentityAcrossConversationWrites() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiChatModelFactory.AiChatClient chatClient = mock(AiChatModelFactory.AiChatClient.class);
        AiLlmServiceConfigProvider configProvider = mock(AiLlmServiceConfigProvider.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("perm-v2", Set.of("ai:chat:send")));
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class),
                null,
                permissionSnapshotService
        );
        AiDTO.ChatRequest request = new AiDTO.ChatRequest();
        request.setMessage("hello");
        AiVO.ChatResponseVO modelResponse = new AiVO.ChatResponseVO();
        modelResponse.setReplyText("OK");

        when(conversationService.ensureConversation(anyLong(), eq("user-uuid-100"), eq(1L), isNull(), any())).thenReturn(10L);
        when(conversationService.recordMessage(eq(100L), eq("user-uuid-100"), eq(10L), eq("USER"), any())).thenReturn(100L);
        when(configProvider.findDefault()).thenReturn(Optional.empty());
        when(chatModelFactory.create(nullable(AiLlmServiceConfig.class))).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenReturn(modelResponse);

        service.chat(currentUser(), request);

        verify(conversationService).ensureConversation(eq(100L), eq("user-uuid-100"), eq(1L), isNull(), any());
        verify(conversationService, org.mockito.Mockito.times(2)).recordMessage(eq(100L), eq("user-uuid-100"), eq(10L), any(), any());
    }

    @Test
    void successfulChatRejectsWhenAuditInsertMisses() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        jdbcTemplate.auditInsertResult = 0;
        AiConversationService conversationService = mock(AiConversationService.class);
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiChatModelFactory.AiChatClient chatClient = mock(AiChatModelFactory.AiChatClient.class);
        AiLlmServiceConfigProvider configProvider = mock(AiLlmServiceConfigProvider.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                mock(AiToolRegistry.class),
                mock(AiSkillPermissionChecker.class),
                mock(AiKnowledgeBaseAppService.class)
        );
        AiDTO.ChatRequest request = new AiDTO.ChatRequest();
        request.setMessage("hello");
        AiVO.ChatResponseVO modelResponse = new AiVO.ChatResponseVO();
        modelResponse.setReplyText("OK");

        when(conversationService.ensureConversation(anyLong(), eq("user-uuid-100"), eq(1L), isNull(), any())).thenReturn(10L);
        when(conversationService.recordMessage(eq(100L), eq("user-uuid-100"), eq(10L), eq("USER"), any())).thenReturn(100L);
        when(configProvider.findDefault()).thenReturn(Optional.empty());
        when(chatModelFactory.create(nullable(AiLlmServiceConfig.class))).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenReturn(modelResponse);

        assertThatThrownBy(() -> service.chat(currentUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI tool audit changed, please retry");
                });
    }

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

        when(conversationService.ensureConversation(anyLong(), any(), anyLong(), isNull(), any())).thenReturn(10L);
        when(conversationService.recordMessage(eq(100L), eq("user-uuid-100"), eq(10L), eq("USER"), any())).thenReturn(100L);
        when(configProvider.findById(isNull())).thenReturn(Optional.empty());
        when(configProvider.findDefaultForEmployee(anyLong())).thenReturn(Optional.empty());
        when(toolRegistry.listRegisteredSkills(anyLong())).thenReturn(List.of());
        when(chatModelFactory.create(nullable(AiLlmServiceConfig.class))).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenThrow(new BizException(ErrorCode.BIZ_ERROR, "LLM 璋冪敤澶辫触: timeout"));

        assertThatThrownBy(() -> service.chat(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessage("LLM 璋冪敤澶辫触: timeout");

        assertThat(jdbcTemplate.lastUpdateSql).contains("insert into ai_tool_audit_log");
        assertThat(jdbcTemplate.lastUpdateArgs[0]).isEqualTo(10L);
        assertThat(jdbcTemplate.lastUpdateArgs[1]).isEqualTo(1L);
        assertThat(jdbcTemplate.lastUpdateArgs[2]).isEqualTo(100L);
        assertThat(jdbcTemplate.lastUpdateArgs[3]).isEqualTo("user-uuid-100");
        assertThat(jdbcTemplate.lastUpdateArgs[4]).isEqualTo("customer.reply");
        assertThat(jdbcTemplate.lastUpdateArgs[6]).isEqualTo("allow");
        assertThat(jdbcTemplate.lastUpdateArgs[9]).isEqualTo("FAIL");
        assertThat(jdbcTemplate.lastUpdateArgs[10]).isEqualTo("LLM 璋冪敤澶辫触: timeout");
        assertThat(jdbcTemplate.lastUpdateArgs[12].toString()).contains("\"code\":\"B0001\"");
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

        doThrow(new BizException(ErrorCode.FORBIDDEN, "鎶€鑳藉凡琚鐢? data.export"))
                .when(permissionChecker)
                .verifyAllowed(anyLong(), eq(List.of("data.export")), eq(false));

        assertThatThrownBy(() -> service.chat(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessage("鎶€鑳藉凡琚鐢? data.export");

        assertThat(jdbcTemplate.lastUpdateSql).contains("insert into ai_tool_audit_log");
        assertThat(jdbcTemplate.lastUpdateArgs[0]).isNull();
        assertThat(jdbcTemplate.lastUpdateArgs[1]).isEqualTo(1L);
        assertThat(jdbcTemplate.lastUpdateArgs[2]).isEqualTo(100L);
        assertThat(jdbcTemplate.lastUpdateArgs[3]).isEqualTo("user-uuid-100");
        assertThat(jdbcTemplate.lastUpdateArgs[4]).isEqualTo("data.export");
        assertThat(jdbcTemplate.lastUpdateArgs[6]).isEqualTo("deny");
        assertThat(jdbcTemplate.lastUpdateArgs[9]).isEqualTo("FAIL");
        assertThat(jdbcTemplate.lastUpdateArgs[10]).isEqualTo("鎶€鑳藉凡琚鐢? data.export");
    }

    @Test
    void skipsSkillPermissionCheckForPlainEmployeeChatWithoutRequestedSkills() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiConversationService conversationService = mock(AiConversationService.class);
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiChatModelFactory.AiChatClient chatClient = mock(AiChatModelFactory.AiChatClient.class);
        AiLlmServiceConfigProvider configProvider = mock(AiLlmServiceConfigProvider.class);
        AiToolRegistry toolRegistry = mock(AiToolRegistry.class);
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        DefaultAiEmployeeRuntimeService service = newService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                toolRegistry,
                permissionChecker,
                mock(AiKnowledgeBaseAppService.class)
        );
        AiDTO.ChatRequest request = chatRequest(List.of());
        AiVO.ChatResponseVO modelResponse = new AiVO.ChatResponseVO();
        modelResponse.setReplyText("OK");

        when(conversationService.ensureConversation(anyLong(), any(), anyLong(), isNull(), any())).thenReturn(10L);
        when(conversationService.recordMessage(eq(100L), eq("user-uuid-100"), eq(10L), eq("USER"), any())).thenReturn(100L);
        when(configProvider.findById(isNull())).thenReturn(Optional.empty());
        when(configProvider.findDefaultForEmployee(anyLong())).thenReturn(Optional.empty());
        when(toolRegistry.listRegisteredSkills(anyLong())).thenReturn(List.of());
        when(chatModelFactory.create(nullable(AiLlmServiceConfig.class))).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenReturn(modelResponse);

        AiVO.ChatResponseVO response = service.chat(currentUser(), request);

        assertThat(response.getConversationId()).isEqualTo(10L);
        verify(permissionChecker, never()).verifyAllowed(anyLong(), anyList(), eq(false));
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
        request.setMessage("鏌ョ湅绯荤粺鏈夊嚑涓敤鎴凤紵");
        AiVO.ToolPlanVO plan = new AiVO.ToolPlanVO();
        plan.setId(9L);
        plan.setStatus("PENDING");
        plan.setRequiresConfirm(false);
        plan.setToolCode("system.user.search");
        AiVO.ToolExecuteResultVO toolResult = new AiVO.ToolExecuteResultVO();
        toolResult.setToolCode("system.user.search");
        toolResult.setResultStatus("SUCCESS");
        toolResult.setMessage("宸ュ叿璋冪敤鎴愬姛");
        toolResult.setData(Map.of("total", 3L, "count", 1, "limit", 1));

        when(conversationService.ensureConversation(anyLong(), any(), anyLong(), isNull(), any())).thenReturn(10L);
        when(conversationService.recordMessage(eq(100L), eq("user-uuid-100"), eq(10L), eq("USER"), any())).thenReturn(100L);
        when(orchestrationService.tryPropose(any(), any())).thenReturn(Optional.of(plan));
        when(orchestrationService.confirm(any(), any())).thenReturn(toolResult);

        AiVO.ChatResponseVO response = service.chat(currentUser(), request);

        assertThat(response.getReplyText()).contains("total users 3");
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
                null,
                null,
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
        return newService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                toolRegistry,
                permissionChecker,
                knowledgeBaseAppService,
                orchestrationService,
                null,
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
            AiToolOrchestrationService orchestrationService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        return newService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                toolRegistry,
                permissionChecker,
                knowledgeBaseAppService,
                orchestrationService,
                permissionSnapshotService,
                null,
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
            AiToolOrchestrationService orchestrationService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        return newService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                toolRegistry,
                permissionChecker,
                knowledgeBaseAppService,
                orchestrationService,
                permissionSnapshotService,
                null,
                sessionAuthenticationService
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
            AiToolOrchestrationService orchestrationService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        return new DefaultAiEmployeeRuntimeService(
                jdbcTemplate,
                configProvider,
                chatModelFactory,
                conversationService,
                toolRegistry,
                permissionChecker,
                knowledgeBaseAppService,
                orchestrationService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService
        );
    }

    private CurrentUser currentUser() {
        return trusted(new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, Set.of("ai:chat:send")));
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(100L, "admin", 1001L, "session-1", 1, false, Set.of("*", "ai:chat:send"));
    }

    private CurrentUser missingSessionVersionUser() {
        return new CurrentUser(100L, "admin", 1001L, "session-1", null, true, Set.of("*", "ai:chat:send"));
    }

    private CurrentUser currentUserWithoutChatPermission() {
        return trusted(new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, Set.of("ai:view")));
    }

    private CurrentUser trusted(CurrentUser currentUser) {
        currentUser.setUserUuid("user-uuid-" + currentUser.getUserId());
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
                username,
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
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
        private boolean queryCalled;
        private int auditInsertResult = 1;

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            this.lastUpdateArgs = args;
            if (sql.contains("insert into ai_tool_audit_log")) {
                return auditInsertResult;
            }
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCalled = true;
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
