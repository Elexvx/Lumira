package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.infrastructure.AiSecretCryptoService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiManagementAppServiceTest {

    @Test
    void listEmployeesShouldRequireViewPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        assertThatThrownBy(() -> service.listEmployees(userWithPermissions(Set.of()), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listEmployeesShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        assertThatThrownBy(() -> service.listEmployees(missingSessionVersionUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listEmployeesShouldRejectMissingUserUuidBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );
        CurrentUser currentUser = currentUser();
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> service.listEmployees(currentUser, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void testLlmServiceShouldRejectMissingPermissionsVersionBeforeProbe() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                chatModelFactory
        );
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion(" ");

        assertThatThrownBy(() -> service.testLlmService(currentUser, testRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(chatModelFactory);
    }

    @Test
    void listEmployeesShouldRejectWhenLiveSnapshotRevokesViewPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("ai:manage")));
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class),
                permissionSnapshotService
        );

        assertThatThrownBy(() -> service.listEmployees(currentUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listEmployeesShouldRejectRevokedSessionTicketBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 100L, "user-uuid-100", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> service.listEmployees(currentUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listEmployeesShouldRejectDisabledTrustedUserBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L)).thenReturn(userSnapshot(100L, "admin", "DISABLED"));
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> service.listEmployees(currentUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listEmployeesShouldRefreshTrustedUsernameFromLiveIdentityBeforeQuery() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L)).thenReturn(userSnapshot(100L, "live-admin", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("*")));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyList()))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyList()))
                .thenReturn(0L);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = currentUser();

        service.listEmployees(currentUser, 1, 10);

        assertThat(currentUser.getUsername()).isEqualTo("live-admin");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
    }

    @Test
    void createLlmServiceShouldRequireManagePermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        assertThatThrownBy(() -> service.createLlmService(userWithPermissions(Set.of("ai:view")), new AiDTO.LlmServiceUpsertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void managementStateWritesShouldBindOriginalBusinessSnapshots() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/ai/app/AiManagementAppService.java"));

        assertThat(source).contains("AiVO.EmployeeDetailVO existing = queryEmployeeDetail(id)");
        assertThat(source).contains("where id = ? and username = ? and enabled = ? and is_deleted = 0");
        assertThat(source).contains("where id = ? and code = ? and provider = ? and enabled = ? and is_deleted = 0");
        assertThat(source).contains("replaceEmployeeCapabilities(existing");
        assertThat(source).contains("where id = ? and username = ? and enabled = ? and is_deleted = 0");
        assertThat(source).contains("permission_mode = case when employee_id = values(employee_id) and skill_code = values(skill_code)");
        assertThat(source).contains("requireAiWrite(updated, \"AI employee changed, please retry\")");
        assertThat(source).contains("requireAiWrite(employeeDeleted, \"AI employee changed, please retry\")");
        assertThat(source).contains("requireAiWrite(updated, \"AI LLM service changed, please retry\")");
        assertThat(source).contains("requireAiWrite(conversationDeleted, \"AI conversation changed, please retry\")");
        assertThat(source).contains("requireAiWrite(inserted, \"AI conversation changed, please retry\")");
    }

    @Test
    void governanceOverviewUsesAggregateStatsQueries() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );
        when(jdbcTemplate.queryForList(contains("from ai_employee")))
                .thenReturn(List.of(Map.of("employeeCount", 5L, "enabledEmployeeCount", 3L)));
        when(jdbcTemplate.queryForList(contains("from ai_llm_service")))
                .thenReturn(List.of(Map.of("llmServiceCount", 4L, "enabledLlmServiceCount", 2L, "missingApiKeyServiceCount", 1L)));
        when(jdbcTemplate.queryForList(contains("from ai_skill")))
                .thenReturn(List.of(Map.of("skillCount", 7L, "highRiskSkillCount", 2L, "confirmationRequiredSkillCount", 4L)));
        when(jdbcTemplate.queryForObject(contains("from ai_employee_skill"), eq(Long.class))).thenReturn(6L);

        AiVO.GovernanceOverviewVO overview = service.governanceOverview(currentUser());
        AiVO.GovernanceOverviewVO cached = service.governanceOverview(currentUser());

        assertThat(overview.getEmployeeCount()).isEqualTo(5L);
        assertThat(cached.getEmployeeCount()).isEqualTo(5L);
        assertThat(overview.getEnabledEmployeeCount()).isEqualTo(3L);
        assertThat(overview.getLlmServiceCount()).isEqualTo(4L);
        assertThat(overview.getEnabledLlmServiceCount()).isEqualTo(2L);
        assertThat(overview.getMissingApiKeyServiceCount()).isEqualTo(1L);
        assertThat(overview.getSkillCount()).isEqualTo(7L);
        assertThat(overview.getHighRiskSkillCount()).isEqualTo(2L);
        assertThat(overview.getConfirmationRequiredSkillCount()).isEqualTo(4L);
        assertThat(overview.getHighRiskAllowedBindingCount()).isEqualTo(6L);
        assertThat(overview.getSampledAt()).isNotNull();
        assertThat(cached.getSampledAt()).isNotNull();
        verify(jdbcTemplate).queryForList(contains("from ai_employee"));
        verify(jdbcTemplate).queryForList(contains("from ai_llm_service"));
        verify(jdbcTemplate).queryForList(contains("from ai_skill"));
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void testLlmServiceReturnsSuccessfulProbeResult() {
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiChatModelFactory.AiChatClient chatClient = mock(AiChatModelFactory.AiChatClient.class);
        AiManagementAppService service = newService(chatModelFactory);
        AiVO.ChatResponseVO response = new AiVO.ChatResponseVO();
        response.setProvider("deepseek");
        response.setModel("deepseek-chat");
        response.setReplyText("OK");
        when(chatModelFactory.create(any())).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenReturn(response);

        AiVO.LlmServiceTestResultVO result = service.testLlmService(currentUser(), testRequest());

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("测试通过");
        assertThat(result.getProvider()).isEqualTo("deepseek");
        assertThat(result.getModel()).isEqualTo("deepseek-chat");
        assertThat(result.getReplyText()).isEqualTo("OK");
        assertThat(result.getLatencyMs()).isNotNull();
    }

    @Test
    void listConversationMessagesShouldAttachMessageFiles() {
        ConversationQueryOperations queryOperations = new ConversationQueryOperations();
        AiManagementAppService service = new AiManagementAppService(
                queryOperations,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        List<AiVO.MessageVO> messages = service.listConversationMessages(currentUser(), 10L);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getAttachments()).hasSize(1);
        assertThat(messages.get(1).getAttachments()).isEmpty();
    }

    @Test
    void listConversationsShouldFilterByOwnerIdAndUuid() {
        ConversationQueryOperations queryOperations = new ConversationQueryOperations();
        AiManagementAppService service = new AiManagementAppService(
                queryOperations,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        service.listConversations(currentUser(), null, 1, 10);

        assertThat(queryOperations.lastQuerySql).contains("c.owner_user_id = ?");
        assertThat(queryOperations.lastQuerySql).contains("c.owner_user_uuid = ?");
        assertThat(queryOperations.lastQueryArgs).containsSequence(100L, "user-uuid-100");
    }

    @Test
    void createConversationShareShouldPersistCreatorUuid() {
        ConversationQueryOperations queryOperations = new ConversationQueryOperations();
        AiManagementAppService service = new AiManagementAppService(
                queryOperations,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        service.createConversationShare(currentUser(), 10L);

        assertThat(queryOperations.lastUpdateSql).contains("created_by_uuid");
        assertThat(queryOperations.lastUpdateSql)
                .contains("from ai_conversation c")
                .contains("c.owner_user_id = ?")
                .contains("c.owner_user_uuid = ?")
                .contains("c.conversation_code = ?")
                .contains("c.status = ?");
        assertThat(queryOperations.lastUpdateArgs).contains("user-uuid-100");
    }

    @Test
    void updateConversationShouldConstrainWriteByOwnerIdAndUuid() {
        ConversationQueryOperations queryOperations = new ConversationQueryOperations();
        AiManagementAppService service = new AiManagementAppService(
                queryOperations,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );
        AiDTO.ConversationUpdateRequest request = new AiDTO.ConversationUpdateRequest();
        request.setTitle("next");
        request.setPinned(true);

        assertThat(service.updateConversation(currentUser(), 10L, request)).isTrue();

        assertThat(queryOperations.lastUpdateSql).contains("owner_user_id = ?");
        assertThat(queryOperations.lastUpdateSql).contains("owner_user_uuid = ?");
        assertThat(queryOperations.lastUpdateArgs).containsSequence(10L, 100L, "user-uuid-100");
    }

    @Test
    void updateConversationShouldRejectWhenSnapshotWriteMisses() {
        ConversationQueryOperations queryOperations = new ConversationQueryOperations();
        queryOperations.updateCount = 0;
        AiManagementAppService service = new AiManagementAppService(
                queryOperations,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );
        AiDTO.ConversationUpdateRequest request = new AiDTO.ConversationUpdateRequest();
        request.setTitle("next");

        assertThatThrownBy(() -> service.updateConversation(currentUser(), 10L, request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI conversation changed, please retry");
                });
    }

    @Test
    void deleteConversationShouldSkipShareCleanupWhenShareTableIsMissing() {
        ConversationQueryOperations queryOperations = new ConversationQueryOperations();
        OperationAuditService operationAuditService = mock(OperationAuditService.class);
        AiManagementAppService service = new AiManagementAppService(
                queryOperations,
                operationAuditService,
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        assertThat(service.deleteConversation(currentUser(), 10L)).isTrue();

        assertThat(queryOperations.updatedTables).contains("ai_conversation", "ai_message", "ai_message_attachment");
        assertThat(queryOperations.updatedTables).doesNotContain("ai_conversation_share");
        assertThat(queryOperations.updateSqlByTable.get("ai_conversation")).contains("owner_user_id = ?", "owner_user_uuid = ?", "conversation_code = ?", "status = ?");
        assertThat(queryOperations.updateSqlByTable.get("ai_message")).contains("from ai_conversation", "owner_user_id = ?", "owner_user_uuid = ?", "conversation_code = ?", "status = ?");
        assertThat(queryOperations.updateSqlByTable.get("ai_message_attachment")).contains("from ai_conversation", "owner_user_id = ?", "owner_user_uuid = ?", "conversation_code = ?", "status = ?");
        assertThat(queryOperations.shareTableExistsChecked).isTrue();
        verify(operationAuditService).log(
                eq(100L),
                eq("user-uuid-100"),
                eq("admin"),
                eq("ai"),
                eq("conversation-delete"),
                eq("DELETE"),
                eq("SUCCESS"),
                contains("conv_10")
        );
    }

    @Test
    void deleteConversationShouldRejectWhenPrimaryWriteMisses() {
        ConversationQueryOperations queryOperations = new ConversationQueryOperations();
        queryOperations.updateCount = 0;
        AiManagementAppService service = new AiManagementAppService(
                queryOperations,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        assertThatThrownBy(() -> service.deleteConversation(currentUser(), 10L))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI conversation changed, please retry");
                });
        assertThat(queryOperations.updatedTables).containsExactly("ai_conversation");
    }

    @Test
    void createConversationShareShouldRejectWhenInsertSelectMisses() {
        ConversationQueryOperations queryOperations = new ConversationQueryOperations();
        queryOperations.updateCount = 0;
        AiManagementAppService service = new AiManagementAppService(
                queryOperations,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        assertThatThrownBy(() -> service.createConversationShare(currentUser(), 10L))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI conversation changed, please retry");
                });
    }

    @Test
    void testLlmServiceRejectsEndpointOverrideWhenReusingStoredApiKey() throws Exception {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AiSecretCryptoService secretCryptoService = mock(AiSecretCryptoService.class);
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                secretCryptoService,
                mock(AiEmployeeRuntimeService.class),
                chatModelFactory
        );
        Object existing = llmServiceRecord("deepseek", "https://api.deepseek.com", "encrypted-secret");
        @SuppressWarnings({"rawtypes", "unchecked"})
        List existingServices = List.of(existing);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any())).thenReturn(existingServices);

        AiDTO.LlmServiceTestRequest request = testRequest();
        request.setServiceId(10L);
        request.setApiKey(null);
        request.setBaseUrl("https://attacker.example");

        assertThatThrownBy(() -> service.testLlmService(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("重新输入 API Key");
    }

    @Test
    void aiChatModelFactoryRejectsPrivateBaseUrl() {
        HttpAiChatModelFactory factory = new HttpAiChatModelFactory(new ObjectMapper());
        AiLlmServiceConfig config = new AiLlmServiceConfig();
        config.setProvider("deepseek");
        config.setDefaultModel("deepseek-chat");
        config.setBaseUrl("http://127.0.0.1:8080");
        config.setApiKey("sk-test");
        AiDTO.ChatRequest chatRequest = new AiDTO.ChatRequest();
        chatRequest.setMessage("ping");
        AiVO.EmployeeDetailVO employee = new AiVO.EmployeeDetailVO();
        employee.setSystemPrompt("system");

        assertThatThrownBy(() -> factory.create(config).chat(chatRequest, employee, List.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内网或本机地址");
    }

    @Test
    void testLlmServiceReturnsFailureProbeResult() {
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiChatModelFactory.AiChatClient chatClient = mock(AiChatModelFactory.AiChatClient.class);
        AiManagementAppService service = newService(chatModelFactory);
        when(chatModelFactory.create(any())).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenThrow(new BizException(ErrorCode.BIZ_ERROR, "LLM 调用失败: 401"));

        AiVO.LlmServiceTestResultVO result = service.testLlmService(currentUser(), testRequest());

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("LLM 调用失败: 401");
        assertThat(result.getProvider()).isEqualTo("deepseek");
        assertThat(result.getModel()).isEqualTo("deepseek-chat");
        assertThat(result.getLatencyMs()).isNotNull();
    }

    @Test
    void createEmployeeShouldRejectDuplicateUsernameViaExistsCheck() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        jdbcTemplate.employeeUsernameExists = true;
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        AiDTO.EmployeeUpsertRequest request = new AiDTO.EmployeeUpsertRequest();
        request.setUsername("assistant");
        request.setNickname("助手");
        request.setDefaultLlmServiceId(null);

        assertThatThrownBy(() -> service.createEmployee(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名已存在");
        assertThat(jdbcTemplate.employeeExistsChecked).isTrue();
        assertThat(jdbcTemplate.countQueryCalled).isFalse();
    }

    @Test
    void createEmployeeShouldKeepBlankSystemPromptEmpty() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        AiDTO.EmployeeUpsertRequest request = new AiDTO.EmployeeUpsertRequest();
        request.setUsername("assistant");
        request.setNickname("鍔╂墜");
        request.setSystemPrompt("   ");

        service.createEmployee(currentUser(), request);

        assertThat(jdbcTemplate.lastUpdateArgs).isNotNull();
        assertThat(jdbcTemplate.lastUpdateArgs[7]).isNull();
    }

    @Test
    void createEmployeeShouldRejectWhenInsertMissesBeforeGeneratedIdLookup() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        jdbcTemplate.updateResults.add(0);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        assertThatThrownBy(() -> service.createEmployee(currentUser(), employeeRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI employee changed, please retry");
                });

        assertThat(jdbcTemplate.lastInsertIdQueries).isZero();
    }

    @Test
    void createLlmServiceShouldRejectDuplicateCodeViaExistsCheck() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        jdbcTemplate.llmServiceCodeExists = true;
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        AiDTO.LlmServiceUpsertRequest request = new AiDTO.LlmServiceUpsertRequest();
        request.setProvider("deepseek");
        request.setCode("deepseek-chat");
        request.setTitle("DeepSeek");

        assertThatThrownBy(() -> service.createLlmService(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("LLM 服务标识已存在");
        assertThat(jdbcTemplate.llmServiceExistsChecked).isTrue();
        assertThat(jdbcTemplate.countQueryCalled).isFalse();
    }

    @Test
    void createLlmServiceShouldRejectWhenInsertMissesBeforeGeneratedIdLookup() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        jdbcTemplate.updateResults.add(0);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        assertThatThrownBy(() -> service.createLlmService(currentUser(), llmServiceRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI LLM service changed, please retry");
                });

        assertThat(jdbcTemplate.lastInsertIdQueries).isZero();
    }

    @Test
    void updateLlmServiceShouldRejectWhenSnapshotWriteMisses() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        jdbcTemplate.updateResults.add(0);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        AiDTO.LlmServiceUpsertRequest request = llmServiceRequest();

        assertThatThrownBy(() -> service.updateLlmService(currentUser(), 1L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("AI LLM service changed");
    }

    @Test
    void updateEmployeeCapabilitiesShouldRejectWhenCapabilityWriteMisses() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        jdbcTemplate.updateResults.add(1);
        jdbcTemplate.updateResults.add(0);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        AiDTO.EmployeeCapabilityItem item = new AiDTO.EmployeeCapabilityItem();
        item.setCapabilityCode("system.user.search");
        item.setPermissionMode("visit");
        AiDTO.EmployeeCapabilitiesUpdateRequest request = new AiDTO.EmployeeCapabilitiesUpdateRequest();
        request.setCapabilities(List.of(item));

        assertThatThrownBy(() -> service.updateEmployeeCapabilities(currentUser(), 1L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("AI employee capability changed");
    }

    @Test
    void listEmployeesShouldSkipCountForFirstShortPage() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        var response = service.listEmployees(currentUser(), 1, 10);

        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(jdbcTemplate.countQueryCalled).isFalse();
    }

    private Object llmServiceRecord(String provider, String baseUrl, String apiKeyEncrypted) throws Exception {
        Class<?> recordType = Class.forName("com.lumira.saas.modules.ai.app.AiManagementAppService$AiEntitiesHelper$LlmServiceRecord");
        Constructor<?> constructor = recordType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object record = constructor.newInstance();
        setRecordValue(recordType, record, "setId", 10L);
        setRecordValue(recordType, record, "setProvider", provider);
        setRecordValue(recordType, record, "setCode", "deepseek");
        setRecordValue(recordType, record, "setTitle", "DeepSeek");
        setRecordValue(recordType, record, "setBaseUrl", baseUrl);
        setRecordValue(recordType, record, "setApiKeyEncrypted", apiKeyEncrypted);
        setRecordValue(recordType, record, "setDefaultModel", "deepseek-chat");
        setRecordValue(recordType, record, "setTimeoutMs", 60000);
        setRecordValue(recordType, record, "setMaxTokens", 64);
        return record;
    }

    private AiDTO.LlmServiceUpsertRequest llmServiceRequest() {
        AiDTO.LlmServiceUpsertRequest request = new AiDTO.LlmServiceUpsertRequest();
        request.setProvider("deepseek");
        request.setCode("deepseek-chat");
        request.setTitle("DeepSeek");
        request.setBaseUrl("https://api.deepseek.com");
        request.setDefaultModel("deepseek-chat");
        request.setEnabled(true);
        request.setTimeoutMs(60000);
        return request;
    }

    private AiDTO.EmployeeUpsertRequest employeeRequest() {
        AiDTO.EmployeeUpsertRequest request = new AiDTO.EmployeeUpsertRequest();
        request.setUsername("assistant");
        request.setNickname("助理");
        request.setDefaultLlmServiceId(null);
        return request;
    }

    private void setRecordValue(Class<?> recordType, Object record, String methodName, Object value) throws Exception {
        Method method = recordType.getMethod(methodName, value.getClass());
        method.setAccessible(true);
        method.invoke(record, value);
    }

    private AiManagementAppService newService(AiChatModelFactory chatModelFactory) {
        return new AiManagementAppService(
                mock(MyBatisQueryOperations.class),
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                chatModelFactory
        );
    }

    private CurrentUser currentUser() {
        return userWithPermissions(Set.of("*"));
    }

    private CurrentUser userWithPermissions(Set<String> permissions) {
        return trusted(new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, permissions));
    }

    private CurrentUser missingSessionVersionUser() {
        return new CurrentUser(100L, "admin", 1001L, "session-1", null, true, Set.of("*"));
    }

    private CurrentUser trusted(CurrentUser currentUser) {
        currentUser.setUserUuid("user-uuid-" + currentUser.getUserId());
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private AiDTO.LlmServiceTestRequest testRequest() {
        AiDTO.LlmServiceTestRequest request = new AiDTO.LlmServiceTestRequest();
        request.setProvider("deepseek");
        request.setCode("deepseek-test");
        request.setTitle("DeepSeek Test");
        request.setBaseUrl("https://api.deepseek.com");
        request.setApiKey("sk-test");
        request.setDefaultModel("deepseek-chat");
        request.setMaxTokens(16);
        return request;
    }

    private static final class ConversationQueryOperations extends MyBatisQueryOperations {
        private final List<String> updatedTables = new java.util.ArrayList<>();
        private final Map<String, String> updateSqlByTable = new java.util.LinkedHashMap<>();
        private boolean shareTableExistsChecked;
        private String lastQuerySql;
        private Object[] lastQueryArgs;
        private String lastUpdateSql;
        private Object[] lastUpdateArgs;
        private int updateCount = 1;

        @Override
        public int update(String sql, Object... args) {
            lastUpdateSql = sql;
            lastUpdateArgs = args;
            if (sql.contains("update ai_conversation_share")) {
                updatedTables.add("ai_conversation_share");
                updateSqlByTable.put("ai_conversation_share", sql);
            } else if (sql.contains("update ai_message_attachment")) {
                updatedTables.add("ai_message_attachment");
                updateSqlByTable.put("ai_message_attachment", sql);
            } else if (sql.contains("update ai_message")) {
                updatedTables.add("ai_message");
                updateSqlByTable.put("ai_message", sql);
            } else if (sql.contains("update ai_conversation")) {
                updatedTables.add("ai_conversation");
                updateSqlByTable.put("ai_conversation", sql);
            }
            return updateCount;
        }

        @Override
        public boolean exists(String sql, Object... args) {
            if (sql.contains("ai_conversation_share")) {
                shareTableExistsChecked = true;
                return false;
            }
            return false;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            lastQuerySql = sql;
            lastQueryArgs = args;
            if (sql.contains("from ai_conversation")) {
                Map<String, Object> conversation = new java.util.LinkedHashMap<>();
                conversation.put("id", 10L);
                conversation.put("owner_user_id", 100L);
                conversation.put("conversation_code", "conv_10");
                conversation.put("title", "测试会话");
                conversation.put("preview", "hello");
                conversation.put("status", "ACTIVE");
                conversation.put("pinned", 0);
                conversation.put("latest_message_at", LocalDateTime.now());
                conversation.put("create_time", LocalDateTime.now());
                conversation.put("update_time", LocalDateTime.now());
                return mapRows(rowMapper, List.of(conversation));
            }
            if (sql.contains("from ai_message_attachment")) {
                Map<String, Object> attachment = new java.util.LinkedHashMap<>();
                attachment.put("id", 1001L);
                attachment.put("file_id", 2001L);
                attachment.put("message_id", 1L);
                attachment.put("original_file_name", "a.txt");
                attachment.put("file_extension", "txt");
                attachment.put("mime_type", "text/plain");
                attachment.put("file_size_bytes", 128L);
                attachment.put("file_size_label", "0.1 KB");
                attachment.put("public_url", "/api/uploads/a.txt");
                attachment.put("preview_url", null);
                attachment.put("download_url", "/api/uploads/download/a.txt");
                attachment.put("preview_mode", "TEXT");
                return mapRows(rowMapper, List.of(
                        attachment
                ));
            }
            if (sql.contains("from ai_message")) {
                return mapRows(rowMapper, List.of(
                        Map.of(
                                "id", 1L,
                                "conversationId", 10L,
                                "role", "USER",
                                "content", "hello",
                                "createTime", LocalDateTime.now()
                        ),
                        Map.of(
                                "id", 2L,
                                "conversationId", 10L,
                                "role", "ASSISTANT",
                                "content", "world",
                                "createTime", LocalDateTime.now()
                        )
                ));
            }
            return List.of();
        }

        private <T> List<T> mapRows(RowMapper<T> rowMapper, List<Map<String, Object>> rows) {
            List<T> mapped = new java.util.ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                try {
                    mapped.add(rowMapper.mapRow(new SqlRow(rows.get(i)), i));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            return mapped;
        }
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean employeeUsernameExists;
        private boolean llmServiceCodeExists;
        private boolean employeeExistsChecked;
        private boolean llmServiceExistsChecked;
        private boolean countQueryCalled;
        private int lastInsertIdQueries;
        private Object[] lastUpdateArgs;
        private final Queue<Integer> updateResults = new ArrayDeque<>();

        @Override
        public int update(String sql, Object... args) {
            lastUpdateArgs = args;
            return updateResults.isEmpty() ? 1 : updateResults.remove();
        }

        @Override
        public boolean exists(String sql, Object... args) {
            if (sql.contains("from ai_employee")) {
                employeeExistsChecked = true;
                return employeeUsernameExists;
            }
            if (sql.contains("from ai_llm_service")) {
                llmServiceExistsChecked = true;
                return llmServiceCodeExists;
            }
            return false;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
            }
            if (sql.contains("select last_insert_id()")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(1L);
            }
            if (sql.contains("from ai_employee_skill")) {
                return requiredType.cast(0L);
            }
            return null;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from ai_llm_service")) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("id", 1L);
                row.put("provider", "deepseek");
                row.put("code", "deepseek-chat");
                row.put("title", "DeepSeek");
                row.put("baseUrl", "https://api.deepseek.com");
                row.put("apiKeyEncrypted", "encrypted");
                row.put("defaultModel", "deepseek-chat");
                row.put("enabled", 1);
                row.put("timeoutMs", 60000);
                row.put("createTime", LocalDateTime.now());
                row.put("updateTime", LocalDateTime.now());
                return mapRows(rowMapper, List.of(row));
            }
            if (sql.contains("from ai_employee")) {
                try {
                    return List.of(rowMapper.mapRow(new SqlRow(Map.of(
                            "id", 1L,                            "username", "assistant",
                            "nickname", "助手",
                            "position", "客服",
                            "enabled", 1,
                            "sortOrder", 1,
                            "createTime", LocalDateTime.now(),
                            "updateTime", LocalDateTime.now()
                    )), 0));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            return List.of();
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("from ai_skill")) {
                return List.of(Map.of(
                        "skillCode", "system.user.search",
                        "readOnly", 1
                ));
            }
            return List.of();
        }

        private <T> List<T> mapRows(RowMapper<T> rowMapper, List<Map<String, Object>> rows) {
            List<T> mapped = new java.util.ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                try {
                    mapped.add(rowMapper.mapRow(new SqlRow(rows.get(i)), i));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            return mapped;
        }
    }
}
