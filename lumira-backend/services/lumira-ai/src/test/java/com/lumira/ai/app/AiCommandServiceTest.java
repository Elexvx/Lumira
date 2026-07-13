package com.lumira.ai.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.ai.dto.AiCommandModels.KnowledgeSearchRequest;
import com.lumira.ai.infrastructure.persistence.JdbcAiConversationRepository;
import com.lumira.ai.infrastructure.persistence.JdbcAiKnowledgeChunkRepository;
import com.lumira.ai.infrastructure.persistence.JdbcAiKnowledgeDocumentRepository;
import com.lumira.ai.infrastructure.persistence.JdbcAiMessageRepository;
import com.lumira.ai.infrastructure.persistence.JdbcAiToolAuditLogRepository;
import com.lumira.ai.infrastructure.persistence.JdbcAiToolCallPlanRepository;
import com.lumira.ai.integration.AiOwnerToolGateway;
import com.lumira.ai.provider.AiProviderRuntime;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiCommandServiceTest {

    @Test
    void searchKnowledgeBoundsLimitAndReturnsRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService readQueryService = mock(AiReadQueryService.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        AiCommandService service = service(jdbcTemplate, readQueryService);

        var references = service.searchKnowledge(user(), new KnowledgeSearchRequest("policy", List.of(1L, 2L), 500));

        assertThat(references).isEmpty();
    }

    @Test
    void searchKnowledgeShouldApplyKnowledgeBaseAclForNonPrivilegedUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        AiCommandService service = service(jdbcTemplate, mock(AiReadQueryService.class));

        var references = service.searchKnowledge(userWithPermissions("ai:knowledge:query"), new KnowledgeSearchRequest("policy", null, 5));

        assertThat(references).isEmpty();
        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("kb.owner_user_id = ?")
                .contains("kb.visibility_scope = 'PLATFORM'")
                .contains("ai_knowledge_base_acl");
    }

    @Test
    void searchKnowledgeRejectsInvalidKnowledgeBaseIdsBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiCommandService service = service(jdbcTemplate, mock(AiReadQueryService.class));

        assertThatThrownBy(() -> service.searchKnowledge(user(), new KnowledgeSearchRequest("policy", List.of(1L, 0L), 5)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void searchKnowledgeRejectsUnauthenticatedUserBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiCommandService service = service(jdbcTemplate, mock(AiReadQueryService.class));

        assertThatThrownBy(() -> service.searchKnowledge(unauthenticatedUser(), new KnowledgeSearchRequest("policy", List.of(1L), 5)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void searchKnowledgeRejectsDisabledTrustedUserBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, "ai-user", "DISABLED"));
        AiCommandService service = service(jdbcTemplate, mock(AiReadQueryService.class), provider(systemInternalApi));

        assertThatThrownBy(() -> service.searchKnowledge(user(), new KnowledgeSearchRequest("policy", List.of(1L), 5)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void searchKnowledgeRejectsBlankLiveUsernameBeforePermissionSnapshotAndDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, " ", "ENABLED"));
        AiCommandService service = service(jdbcTemplate, mock(AiReadQueryService.class), provider(systemInternalApi));

        assertThatThrownBy(() -> service.searchKnowledge(user(), new KnowledgeSearchRequest("policy", List.of(1L), 5)))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });

        verify(systemInternalApi, never()).permissionSnapshot(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void searchKnowledgeShouldRequireLiveKnowledgeQueryPermissionBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiCommandService service = service(
                jdbcTemplate,
                mock(AiReadQueryService.class),
                provider(trustedSystemInternalApi(List.of("ai:chat:send")), false)
        );

        assertThatThrownBy(() -> service.searchKnowledge(user(), new KnowledgeSearchRequest("policy", List.of(1L), 5)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void executeLocalPermissionSnapshotTool() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        when(jdbcTemplate.update(contains("insert into ai_tool_audit_log"), any(Object[].class))).thenReturn(1);
        AiCommandService service = service(jdbcTemplate, readQueryService(jdbcTemplate));

        var result = service.executeTool(user(), new com.lumira.ai.dto.AiCommandModels.ToolExecuteRequest(
                1L,
                null,
                "system.permission.snapshot",
                java.util.Map.of(),
                true
        ));

        assertThat(result.resultStatus()).isEqualTo("SUCCESS");
        assertThat(result.data()).containsEntry("userId", 7L);
        assertThat(result.data()).containsEntry("remoteOwnerCall", false);
        assertThat(result.data()).containsEntry("degraded", false);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Integer.class), eq(1L));
    }

    @Test
    void executeToolRejectsUnauthenticatedUserBeforeEmployeeLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiCommandService service = service(jdbcTemplate, readQueryService(jdbcTemplate));

        assertThatThrownBy(() -> service.executeTool(
                unauthenticatedUser(),
                new com.lumira.ai.dto.AiCommandModels.ToolExecuteRequest(
                        1L,
                        null,
                        "system.permission.snapshot",
                        java.util.Map.of(),
                        true
                )
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void executeToolShouldRequireLiveToolExecutePermissionBeforeEmployeeLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiCommandService service = service(
                jdbcTemplate,
                readQueryService(jdbcTemplate),
                provider(trustedSystemInternalApi(List.of("system:permission:snapshot")), false)
        );

        assertThatThrownBy(() -> service.executeTool(
                user(),
                new com.lumira.ai.dto.AiCommandModels.ToolExecuteRequest(
                        1L,
                        null,
                        "system.permission.snapshot",
                        java.util.Map.of(),
                        true
                )
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void searchKnowledgeRejectsBlankUsernameBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiCommandService service = service(jdbcTemplate, mock(AiReadQueryService.class));

        assertThatThrownBy(() -> service.searchKnowledge(blankUsernameUser(), new KnowledgeSearchRequest("policy", List.of(1L), 5)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void searchKnowledgeRejectsMissingSessionVersionBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiCommandService service = service(jdbcTemplate, mock(AiReadQueryService.class));

        assertThatThrownBy(() -> service.searchKnowledge(missingSessionVersionUser(), new KnowledgeSearchRequest("policy", List.of(1L), 5)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void executeToolRejectsDisabledOrMissingEmployee() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(404L))).thenReturn(0);
        AiCommandService service = service(jdbcTemplate, readQueryService(jdbcTemplate));

        assertThrows(com.lumira.common.exception.BizException.class, () -> service.executeTool(
                user(),
                new com.lumira.ai.dto.AiCommandModels.ToolExecuteRequest(
                        404L,
                        null,
                        "system.permission.snapshot",
                        java.util.Map.of(),
                        true
                )
        ));
    }

    @Test
    void executeToolShouldRequireDeclaredToolPermission() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiCommandService service = service(jdbcTemplate, readQueryService(jdbcTemplate));

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

    @Test
    void confirmToolShouldWriteBackWithOwnerUserUuidBoundary() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForMap(anyString(), eq(7L), eq("user-uuid-7"), eq(9L))).thenReturn(Map.of(
                "id", 9L,
                "employee_id", 1L,
                "conversation_id", 3L,
                "tool_code", "system.permission.snapshot",
                "arguments_json", "{}"
        ));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        when(jdbcTemplate.update(contains("update ai_tool_call_plan"), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.update(contains("insert into ai_tool_audit_log"), any(Object[].class))).thenReturn(1);
        AiCommandService service = service(jdbcTemplate, readQueryService(jdbcTemplate));

        service.confirmTool(user(), new com.lumira.ai.dto.AiCommandModels.ToolConfirmRequest(9L));

        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), any(Object[].class));
        String planUpdateSql = sqlCaptor.getAllValues().stream()
                .filter(sql -> sql.contains("update ai_tool_call_plan"))
                .findFirst()
                .orElseThrow();
        assertThat(planUpdateSql)
                .contains("confirmed_by_uuid = ?")
                .contains("owner_user_id = ?")
                .contains("owner_user_uuid = ?")
                .contains("status = 'PENDING'");
        assertThat(sqlCaptor.getAllValues().stream()
                .filter(sql -> sql.contains("update ai_tool_call_plan"))
                .toList())
                .anySatisfy(sql -> assertThat(sql).contains("status = ?", "status = 'EXECUTING'"));
        verify(jdbcTemplate, atLeastOnce()).update(
                contains("update ai_tool_call_plan"),
                any(Object[].class)
        );
    }

    @Test
    void confirmToolShouldRejectDuplicateClaimBeforeExecution() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForMap(anyString(), eq(7L), eq("user-uuid-7"), eq(9L))).thenReturn(Map.of(
                "id", 9L,
                "employee_id", 1L,
                "conversation_id", 3L,
                "tool_code", "system.permission.snapshot",
                "arguments_json", "{}"
        ));
        when(jdbcTemplate.update(contains("update ai_tool_call_plan"), any(Object[].class))).thenReturn(0);
        AiCommandService service = service(jdbcTemplate, readQueryService(jdbcTemplate));

        assertThatThrownBy(() -> service.confirmTool(user(), new com.lumira.ai.dto.AiCommandModels.ToolConfirmRequest(9L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("changed");

        verify(jdbcTemplate, never()).update(contains("insert into ai_tool_audit_log"), any(Object[].class));
    }

    @Test
    void chatShouldConstrainLatestMessageUpdateByConversationOwnerUuid() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService readQueryService = mock(AiReadQueryService.class);
        when(jdbcTemplate.queryForMap(
                contains("from ai_conversation"),
                eq(7L),
                eq("user-uuid-7"),
                eq(3L)
        )).thenReturn(Map.of("id", 3L, "conversation_code", "conv-3"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.update(contains("insert into ai_message"), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.update(contains("update ai_conversation"), any(Object[].class))).thenReturn(1);
        AiCommandService service = service(jdbcTemplate, readQueryService);

        service.chat(user(), new com.lumira.ai.dto.AiCommandModels.ChatRequest(
                1L,
                null,
                3L,
                null,
                "hello",
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true
        ));

        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), any(Object[].class));
        String conversationUpdateSql = sqlCaptor.getAllValues().stream()
                .filter(sql -> sql.contains("update ai_conversation"))
                .findFirst()
                .orElseThrow();
        assertThat(conversationUpdateSql)
                .contains("owner_user_id = ?")
                .contains("owner_user_uuid = ?")
                .contains("is_deleted = 0");
    }

    @Test
    void chatShouldRequireLiveChatPermissionBeforeEmployeeLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService readQueryService = mock(AiReadQueryService.class);
        AiCommandService service = service(
                jdbcTemplate,
                readQueryService,
                provider(trustedSystemInternalApi(List.of("ai:knowledge:query")), false)
        );

        assertThatThrownBy(() -> service.chat(user(), new com.lumira.ai.dto.AiCommandModels.ChatRequest(
                1L,
                null,
                3L,
                null,
                "hello",
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true
        )))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(readQueryService);
    }

    @Test
    void uploadKnowledgeDocumentRejectsOversizedFileBeforeReadingBytes() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService readQueryService = mock(AiReadQueryService.class);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((10L * 1024L * 1024L) + 1L);
        AiCommandService service = service(jdbcTemplate, readQueryService);

        assertThatThrownBy(() -> service.uploadKnowledgeDocument(user(), 1L, file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(file, never()).getBytes();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void uploadKnowledgeDocumentShouldRequireManageableKnowledgeBase() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService readQueryService = mock(AiReadQueryService.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = user();
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((10L * 1024L * 1024L) + 1L);
        AiCommandService service = service(jdbcTemplate, readQueryService);

        assertThatThrownBy(() -> service.uploadKnowledgeDocument(currentUser, 11L, file))
                .isInstanceOf(BizException.class);

        verify(readQueryService).requireManageableKnowledgeBase(currentUser, 11L);
        verify(readQueryService, never()).getKnowledgeBase(any(), eq(11L));
    }

    @Test
    void uploadKnowledgeDocumentShouldRequireLiveUploadPermissionBeforeKnowledgeBaseCheck() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiReadQueryService readQueryService = mock(AiReadQueryService.class);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        AiCommandService service = service(
                jdbcTemplate,
                readQueryService,
                provider(trustedSystemInternalApi(List.of("ai:knowledge:query")), false)
        );

        assertThatThrownBy(() -> service.uploadKnowledgeDocument(user(), 11L, file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(readQueryService);
    }

    @Test
    void knowledgeDocumentInsertShouldPersistTrustedUserUuid() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/lumira/ai/infrastructure/persistence/JdbcAiKnowledgeDocumentRepository.java"
        ));

        assertThat(source)
                .contains("created_by, created_by_uuid, updated_by, updated_by_uuid")
                .contains("ps.setString(10, operatorUuid)")
                .contains("ps.setString(12, operatorUuid)");
    }

    @Test
    void knowledgeDocumentUpdatesShouldConstrainAccessibleOwnerUuidBoundary() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcAiKnowledgeDocumentRepository repository = new JdbcAiKnowledgeDocumentRepository(jdbcTemplate);
        CurrentUser currentUser = managedByRoleAndDepartment();

        repository.updateChunkCount(currentUser, 11L, 99L, 3, java.time.LocalDateTime.now());
        repository.markIndexed(currentUser, 11L, 99L, 12, 3, java.time.LocalDateTime.now());
        repository.softDeleteDocument(currentUser, 11L, 99L, java.time.LocalDateTime.now());

        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(3)).update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getAllValues()).allSatisfy(sql -> assertThat(sql)
                .contains("knowledge_base_id = ?")
                .contains("is_deleted = 0")
                .contains("exists (")
                .contains("kb.owner_user_id = ?")
                .contains("kb.owner_user_uuid = ?")
                .contains("acl.permission = 'MANAGE'")
                .contains("acl.subject_type = 'ROLE'")
                .contains("acl.subject_type = 'DEPARTMENT'")
                .doesNotContain("kb.visibility_scope = 'PLATFORM'")
                .doesNotContain("acl.permission in ('VIEW', 'USE', 'MANAGE')"));
        assertThat(sqlCaptor.getAllValues().get(2)).contains("update ai_knowledge_document");
        assertThat(sqlCaptor.getAllValues().get(2)).doesNotContain("set is_deleted = 1, update_time = ? where id = ?");
    }

    @Test
    void knowledgeDocumentReadShouldConstrainAccessibleOwnerUuidBoundary() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("extracted_text", "hello"));
        JdbcAiKnowledgeDocumentRepository repository = new JdbcAiKnowledgeDocumentRepository(jdbcTemplate);
        CurrentUser currentUser = managedByRoleAndDepartment();

        assertThat(repository.findExtractedText(currentUser, 11L, 99L)).isEqualTo("hello");

        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("knowledge_base_id = ?")
                .contains("is_deleted = 0")
                .contains("exists (")
                .contains("kb.owner_user_id = ?")
                .contains("kb.owner_user_uuid = ?")
                .contains("acl.permission = 'MANAGE'")
                .contains("acl.subject_type = 'ROLE'")
                .contains("acl.subject_type = 'DEPARTMENT'")
                .doesNotContain("kb.visibility_scope = 'PLATFORM'")
                .doesNotContain("acl.permission in ('VIEW', 'USE', 'MANAGE')");
    }

    @Test
    void knowledgeDocumentUpdateShouldRejectWhenTrustedBoundaryMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        JdbcAiKnowledgeDocumentRepository repository = new JdbcAiKnowledgeDocumentRepository(jdbcTemplate);

        assertThatThrownBy(() -> repository.markIndexed(
                managedByRoleAndDepartment(),
                11L,
                99L,
                12,
                3,
                java.time.LocalDateTime.now()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("state changed");
    }

    @Test
    void knowledgeChunkSoftDeleteShouldConstrainAccessibleOwnerUuidBoundary() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcAiKnowledgeChunkRepository repository = new JdbcAiKnowledgeChunkRepository(jdbcTemplate);
        CurrentUser currentUser = managedByRoleAndDepartment();

        repository.softDeleteByDocument(currentUser, 11L, 99L, java.time.LocalDateTime.now());

        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("document_id = ?")
                .contains("knowledge_base_id = ?")
                .contains("is_deleted = 0")
                .contains("exists (")
                .contains("kb.owner_user_id = ?")
                .contains("kb.owner_user_uuid = ?")
                .contains("acl.permission = 'MANAGE'")
                .contains("acl.subject_type = 'ROLE'")
                .contains("acl.subject_type = 'DEPARTMENT'")
                .doesNotContain("kb.visibility_scope = 'PLATFORM'")
                .doesNotContain("acl.permission in ('VIEW', 'USE', 'MANAGE')");
    }

    @Test
    void knowledgeChunkInsertShouldRejectWhenWriteMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        JdbcAiKnowledgeChunkRepository repository = new JdbcAiKnowledgeChunkRepository(jdbcTemplate);

        assertThatThrownBy(() -> repository.addChunk(
                11L,
                99L,
                0,
                "hello",
                "hello",
                1,
                "test-embedding",
                2,
                "[1,0]",
                java.time.LocalDateTime.now()
        ))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void conversationLatestMessageShouldRejectWhenWriteMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        JdbcAiConversationRepository repository = new JdbcAiConversationRepository(jdbcTemplate);

        assertThatThrownBy(() -> repository.updateLatestMessageAt(
                7L,
                "user-uuid-7",
                99L,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now()
        ))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void messageInsertShouldRejectWhenWriteMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        JdbcAiMessageRepository repository = new JdbcAiMessageRepository(jdbcTemplate);

        assertThatThrownBy(() -> repository.addMessage(99L, "USER", "hello", java.time.LocalDateTime.now()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void toolAuditInsertShouldRejectWhenWriteMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        JdbcAiToolAuditLogRepository repository = new JdbcAiToolAuditLogRepository(jdbcTemplate);

        assertThatThrownBy(() -> repository.addAuditLog(
                99L,
                12L,
                7L,
                "user-uuid-7",
                "tool.code",
                "Tool",
                false,
                true,
                7L,
                "user-uuid-7",
                java.time.LocalDateTime.now(),
                "SUCCESS",
                "ok",
                "{}",
                "{}",
                java.time.LocalDateTime.now()
        ))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    private AiCommandService service(JdbcTemplate jdbcTemplate, AiReadQueryService readQueryService) {
        return service(jdbcTemplate, readQueryService, provider(enabledSystemInternalApi()));
    }

    private AiReadQueryService readQueryService(JdbcTemplate jdbcTemplate) {
        return AiReadQueryServiceFixture.create(
                jdbcTemplate,
                provider(enabledSystemInternalApi()),
                () -> List.of(new AiToolVO(
                        "system.permission.snapshot",
                        "Permission snapshot",
                        "system",
                        "Current permissions",
                        "LOW",
                        true,
                        false,
                        null,
                        Map.of()
                ))
        );
    }

    private AiCommandService service(
            JdbcTemplate jdbcTemplate,
            AiReadQueryService readQueryService,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        return new AiCommandService(
                new JdbcAiKnowledgeDocumentRepository(jdbcTemplate),
                new JdbcAiKnowledgeChunkRepository(jdbcTemplate),
                new JdbcAiConversationRepository(jdbcTemplate),
                new JdbcAiMessageRepository(jdbcTemplate),
                new JdbcAiToolCallPlanRepository(jdbcTemplate),
                new JdbcAiToolAuditLogRepository(jdbcTemplate),
                readQueryService,
                noOpGateway(),
                noOpProvider(),
                new ObjectMapper(),
                new PermissionGuard(),
                systemInternalApiProvider
        );
    }

    private AiOwnerToolGateway noOpGateway() {
        return new AiOwnerToolGateway() {
            @Override
            public ToolExecution execute(CurrentUser currentUser, AiToolVO tool, Map<String, Object> arguments) {
                return new ToolExecution(Map.of(
                        "userId", currentUser.getUserId(),
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
        return trusted(new CurrentUser(7L, "ai-user", null, "s1", 1, true, Set.of("*")));
    }

    private SystemInternalApi enabledSystemInternalApi() {
        return trustedSystemInternalApi(List.of(
                "ai:knowledge:document:upload",
                "ai:knowledge:document:index",
                "ai:knowledge:query",
                "ai:chat:send",
                "ai:tool:execute",
                "system:permission:snapshot"
        ));
    }

    private CurrentUser userWithPermissions(String... permissions) {
        return trusted(new CurrentUser(7L, "ai-user", 2002L, "s1", 1, true, Set.of(permissions)));
    }

    private CurrentUser managedByRoleAndDepartment() {
        CurrentUser currentUser = userWithPermissions("ai:knowledge:update");
        currentUser.setRoleIds(Set.of(9L));
        currentUser.setPrimaryDeptId(15L);
        currentUser.setDeptIds(Set.of(18L));
        return currentUser;
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(7L, "ai-user", 2002L, "s1", 1, false, Set.of("*"));
    }

    private CurrentUser blankUsernameUser() {
        return new CurrentUser(7L, " ", 2002L, "s1", 1, true, Set.of("*"));
    }

    private CurrentUser missingSessionVersionUser() {
        return new CurrentUser(7L, "ai-user", 2002L, "s1", null, true, Set.of("*"));
    }

    private CurrentUser trusted(CurrentUser currentUser) {
        currentUser.setUserUuid("user-uuid-" + currentUser.getUserId());
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private ObjectProvider<SystemInternalApi> provider(SystemInternalApi systemInternalApi) {
        return provider(systemInternalApi, true);
    }

    private ObjectProvider<SystemInternalApi> provider(SystemInternalApi systemInternalApi, boolean stubSnapshot) {
        if (systemInternalApi != null && stubSnapshot) {
            when(systemInternalApi.permissionSnapshot(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> permissionSnapshot(invocation.getArgument(0, Long.class)));
        }
        ObjectProvider<SystemInternalApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(systemInternalApi);
        return provider;
    }

    private SystemInternalApi trustedSystemInternalApi(List<String> permissions) {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, "ai-user", "ENABLED"));
        when(systemInternalApi.permissionSnapshot(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> permissionSnapshot(invocation.getArgument(0, Long.class), permissions));
        return systemInternalApi;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private PermissionSnapshotDTO permissionSnapshot(Long userId) {
        return permissionSnapshot(
                userId,
                List.of(
                        "ai:knowledge:document:upload",
                        "ai:knowledge:document:index",
                        "ai:knowledge:query",
                        "ai:chat:send",
                        "ai:tool:execute",
                        "system:permission:snapshot"
                )
        );
    }

    private PermissionSnapshotDTO permissionSnapshot(Long userId, List<String> permissions) {
        return new PermissionSnapshotDTO(
                "perm-v" + userId,
                permissions,
                List.of(11L),
                21L,
                List.of(21L),
                List.of(21L, 22L),
                List.of(),
                "/ai"
        );
    }
}
