package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiKnowledgeBaseAppServiceTest {

    @Test
    void listKnowledgeBasesShouldRejectUnauthenticatedUserBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        assertThatThrownBy(() -> service.listKnowledgeBases(unauthenticatedUser(), null, null, "OWNED", 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.queryCalled).isFalse();
        assertThat(queryOperations.updateSql).isEmpty();
    }

    @Test
    void listKnowledgeBasesShouldRejectBlankUsernameBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        assertThatThrownBy(() -> service.listKnowledgeBases(blankUsernameUser(), null, null, "OWNED", 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.queryCalled).isFalse();
        assertThat(queryOperations.updateSql).isEmpty();
    }

    @Test
    void listKnowledgeBasesShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        assertThatThrownBy(() -> service.listKnowledgeBases(missingSessionVersionUser(), null, null, "OWNED", 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.queryCalled).isFalse();
        assertThat(queryOperations.updateSql).isEmpty();
    }

    @Test
    void listKnowledgeBasesShouldRejectRevokedSessionTicketBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService(),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> service.listKnowledgeBases(
                trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:view"))),
                null,
                null,
                "OWNED",
                1,
                10
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(queryOperations.queryCalled).isFalse();
        assertThat(queryOperations.updateSql).isEmpty();
    }

    @Test
    void createKnowledgeBaseShouldRejectUnauthenticatedUserBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        AiDTO.KnowledgeBaseUpsertRequest request = new AiDTO.KnowledgeBaseUpsertRequest();
        request.setName("private docs");
        request.setVisibilityScope("PERSONAL");

        assertThatThrownBy(() -> service.createKnowledgeBase(unauthenticatedUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.queryCalled).isFalse();
        assertThat(queryOperations.updateSql).isEmpty();
    }

    @Test
    void listEmployeeKnowledgeBasesShouldRequireViewPermissionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:view")));

        assertThatThrownBy(() -> service.listEmployeeKnowledgeBases(currentUser, 11L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.queryCalled).isFalse();
        assertThat(queryOperations.updateSql).isEmpty();
    }

    @Test
    void updateEmployeeKnowledgeBasesShouldRequireManagePermissionBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:view")));
        AiDTO.EmployeeKnowledgeBasesUpdateRequest request = new AiDTO.EmployeeKnowledgeBasesUpdateRequest();
        request.setKnowledgeBaseIds(List.of(20L));

        assertThatThrownBy(() -> service.updateEmployeeKnowledgeBases(currentUser, 11L, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.existsCalled).isFalse();
        assertThat(queryOperations.queryCalled).isFalse();
        assertThat(queryOperations.updateSql).isEmpty();
    }

    @Test
    void listEmployeeKnowledgeBasesShouldRejectWhenLiveSnapshotRevokesViewPermissionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(7L, "user-uuid-7")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(7L, "user-uuid-7"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("ai:knowledge:view")));
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService(),
                permissionSnapshotService
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:view", "ai:knowledge:view")));

        assertThatThrownBy(() -> service.listEmployeeKnowledgeBases(currentUser, 11L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.queryCalled).isFalse();
        assertThat(queryOperations.updateSql).isEmpty();
    }

    @Test
    void listKnowledgeBasesShouldRejectDisabledTrustedUserBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, "admin", "DISABLED"));
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> service.listKnowledgeBases(
                trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:view"))),
                null,
                null,
                "OWNED",
                1,
                10
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.queryCalled).isFalse();
        assertThat(queryOperations.updateSql).isEmpty();
    }

    @Test
    void listKnowledgeBasesShouldRefreshTrustedUsernameFromLiveIdentityBeforeQuery() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, "live-admin", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(7L, "user-uuid-7")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(7L, "user-uuid-7"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("ai:knowledge:view")));
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:view")));

        service.listKnowledgeBases(currentUser, null, null, "OWNED", 1, 10);

        assertThat(currentUser.getUsername()).isEqualTo("live-admin");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
        assertThat(queryOperations.queryCalled).isTrue();
    }

    @Test
    void employeeKnowledgeBaseBindingShouldRevalidateEmployeeBusinessContext() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/ai/app/AiKnowledgeBaseAppService.java"));

        assertThat(source)
                .doesNotContain("update ai_employee_knowledge_base set is_deleted = 1, update_time = ? where employee_id = ? and is_deleted = 0")
                .doesNotContain("on duplicate key update is_deleted = 0, update_time = values(update_time)")
                .contains("EmployeeBindingContext employee = requireEmployeeBindingContext(employeeId)")
                .contains("e.id = ai_employee_knowledge_base.employee_id")
                .contains("and e.username = ?")
                .contains("and e.enabled = ?");
    }

    @Test
    void ownedKnowledgeBaseListUsesBaseCountersAndKeepsWhereClauseSeparated() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:view")));

        service.listKnowledgeBases(currentUser, null, null, "OWNED", 1, 10);

        assertFalse(queryOperations.lastListSql.contains("?left join"));
        assertFalse(queryOperations.lastListSql.contains("ai_knowledge_base_stats"));
        assertTrue(queryOperations.lastListSql.contains("coalesce(kb.document_count, 0) as document_count"));
        assertThat(queryOperations.lastListSql).contains("kb.owner_user_id = ? and kb.owner_user_uuid = ?");
        assertThat(queryOperations.countQueryCount).isZero();
    }

    @Test
    void pendingIndexJobReadsFileAndBuildsChunks() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        InMemoryFileInternalApi fileInternalApi = new InMemoryFileInternalApi("hello knowledge world".getBytes(StandardCharsets.UTF_8));
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                fileInternalApi,
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        assertThat(queryOperations.lastListSql).contains("status = 'FAILED'");
        assertThat(queryOperations.lastListSql).contains("index_next_retry_at");
        assertThat(queryOperations.lastListSql).contains("u.status = 'ENABLED'");
        assertThat(queryOperations.updateSql).anySatisfy(sql -> assertThat(sql).contains("index_claim_token", "index_claim_expires_at"));
        assertThat(queryOperations.updateSql).anySatisfy(sql -> assertThat(sql).contains("set extracted_text"));
        assertThat(queryOperations.updateSql).anySatisfy(sql -> assertThat(sql).contains("insert into ai_knowledge_chunk"));
        assertThat(queryOperations.updateSql).anySatisfy(sql -> assertThat(sql).contains("status = 'READY'"));
        assertThat(fileInternalApi.lastReadUserId).isEqualTo(7L);
        assertThat(fileInternalApi.lastReadUsername).isEqualTo("admin");
    }

    @Test
    void pendingIndexJobRejectsPartialChunkBatchInsert() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.chunkInsertResult = 0;
        InMemoryFileInternalApi fileInternalApi = new InMemoryFileInternalApi("hello knowledge world".getBytes(StandardCharsets.UTF_8));
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                fileInternalApi,
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        assertThat(queryOperations.updateSql).anySatisfy(sql ->
                assertThat(sql).contains("insert into ai_knowledge_chunk"));
        assertThat(queryOperations.updateSql).anySatisfy(sql ->
                assertThat(sql).contains("index_last_error"));
        assertThat(queryOperations.updateSql).noneSatisfy(sql ->
                assertThat(sql).contains("status = 'READY'"));
    }

    @Test
    void pendingIndexJobUsesOwnerIdentityWithoutSyntheticTrustedCurrentUser() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/ai/app/AiKnowledgeBaseAppService.java"));

        assertThat(source).doesNotContain("Set.of(\"*\")");
        assertThat(source).doesNotContain("CurrentUser jobUser = new CurrentUser(");
        assertThat(source).contains("IndexOwnerContext owner = requireIndexOwner(task);");
        assertThat(source).doesNotContain("aiIndexerPermissionsVersion(task)");
    }

    @Test
    void uploadDocumentProcessesIndexImmediatelyWhenJobExecutorIsUnavailable() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        InMemoryFileInternalApi fileInternalApi = new InMemoryFileInternalApi("uploaded knowledge text".getBytes(StandardCharsets.UTF_8));
        PlatformEventPublisher platformEventPublisher = mock(PlatformEventPublisher.class);
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                fileInternalApi,
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                platformEventPublisher,
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:document:upload")));
        MultipartFile file = new TestMultipartFile("manual.txt", "text/plain", "uploaded knowledge text".getBytes(StandardCharsets.UTF_8));

        var result = service.uploadDocument(currentUser, 20L, file);

        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(fileInternalApi.uploadedForUserId).isEqualTo(7L);
        assertThat(fileInternalApi.uploadedForUserUuid).isEqualTo("user-uuid-7");
        assertThat(fileInternalApi.uploadedForUsername).isEqualTo("admin");
        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("insert into ai_knowledge_document", "created_by_uuid", "updated_by_uuid");
            assertThat(call.args()).contains(7L, "user-uuid-7");
        });
        assertThat(queryOperations.updateSql).anySatisfy(sql -> assertThat(sql).contains("set extracted_text"));
        assertThat(queryOperations.updateSql).anySatisfy(sql -> assertThat(sql).contains("insert into ai_knowledge_chunk"));
        assertThat(queryOperations.updateSql).anySatisfy(sql -> assertThat(sql).contains("status = 'READY'"));
        verify(platformEventPublisher).publishAfterCommit(
                any(),
                eq("AI_KNOWLEDGE_DOCUMENT_INDEXED"),
                eq(7L),
                eq("ai.knowledge-document"),
                eq(30L),
                org.mockito.ArgumentMatchers.argThat(attributes ->
                        "user-uuid-7".equals(attributes.get("userUuid"))
                                && Long.valueOf(20L).equals(attributes.get("knowledgeBaseId"))
                                && Long.valueOf(30L).equals(attributes.get("documentId"))
                )
        );
    }

    @Test
    void uploadDocumentShouldRejectWhenInsertMissesBeforeDocumentLookup() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.knowledgeDocumentInsertResult = 0;
        InMemoryFileInternalApi fileInternalApi = new InMemoryFileInternalApi("uploaded knowledge text".getBytes(StandardCharsets.UTF_8));
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                fileInternalApi,
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:document:upload")));
        MultipartFile file = new TestMultipartFile("manual.txt", "text/plain", "uploaded knowledge text".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadDocument(currentUser, 20L, file))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Knowledge document changed, please retry");
                });

        assertThat(queryOperations.documentIdLookupQueries).isZero();
    }

    @Test
    void deleteDocumentShouldBindDocumentCreatorIdentityInFinalWrites() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:manage")));

        service.deleteDocument(currentUser, 20L, 30L);

        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("update ai_knowledge_document");
            assertThat(call.sql()).contains("created_by = ?");
            assertThat(call.sql()).contains("created_by_uuid = ?");
            assertThat(call.args()).contains(7L, "user-uuid-7");
        });
        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("update ai_knowledge_chunk");
            assertThat(call.sql()).contains("created_by = ?");
            assertThat(call.sql()).contains("created_by_uuid = ?");
            assertThat(call.args()).contains(7L, "user-uuid-7");
        });
    }

    @Test
    void deleteDocumentShouldRejectWhenFinalWriteMisses() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.knowledgeDocumentDeleteResult = 0;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:manage")));

        assertThatThrownBy(() -> service.deleteDocument(currentUser, 20L, 30L))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Knowledge document changed, please retry");
                });
    }

    @Test
    void pendingIndexJobPrefersFileTextArtifactWhenAvailable() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        InMemoryFileInternalApi fileInternalApi = new InMemoryFileInternalApi("raw file should not be parsed".getBytes(StandardCharsets.UTF_8));
        fileInternalApi.textArtifact = "artifact text from file owner";
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                fileInternalApi,
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        assertThat(fileInternalApi.contentReadCount).isZero();
        RecordingQueryOperations.UpdateCall extractedUpdate = queryOperations.updateCalls.stream()
                .filter(call -> call.sql().contains("set extracted_text"))
                .findFirst()
                .orElseThrow();
        assertThat(extractedUpdate.args()[0]).isEqualTo("artifact text from file owner");
        assertThat(extractedUpdate.sql()).contains("file_id = ?", "status = 'INDEXING'", "index_claim_token = ?", "created_by = ?", "created_by_uuid = ?");
        assertThat(extractedUpdate.args()).contains(7L, "user-uuid-7");
        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("update ai_knowledge_chunk", "exists", "created_by = ?", "created_by_uuid = ?");
            assertThat(call.args()).contains(7L, "user-uuid-7");
        });
        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("status = 'READY'", "index_claim_token = ?", "created_by = ?", "created_by_uuid = ?");
            assertThat(call.args()).contains(7L, "user-uuid-7");
        });
    }

    @Test
    void pendingIndexJobRejectsMissingOwnerWithoutReadingFile() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.createdBy = 0L;
        InMemoryFileInternalApi fileInternalApi = new InMemoryFileInternalApi("raw file should not be parsed".getBytes(StandardCharsets.UTF_8));
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                fileInternalApi,
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        assertThat(fileInternalApi.contentReadCount).isZero();
        RecordingQueryOperations.UpdateCall failure = queryOperations.updateCalls.stream()
                .filter(call -> call.sql().contains("set status = ?"))
                .findFirst()
                .orElseThrow();
        assertThat(failure.args()[0]).isEqualTo("FAILED");
        assertThat(failure.args()[5]).isNull();
    }

    @Test
    void pendingIndexJobRejectsMissingOwnerUuidWithoutReadingFile() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.createdByUserUuid = null;
        InMemoryFileInternalApi fileInternalApi = new InMemoryFileInternalApi("raw file should not be parsed".getBytes(StandardCharsets.UTF_8));
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                fileInternalApi,
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        assertThat(fileInternalApi.contentReadCount).isZero();
        RecordingQueryOperations.UpdateCall failure = queryOperations.updateCalls.stream()
                .filter(call -> call.sql().contains("set status = ?"))
                .findFirst()
                .orElseThrow();
        assertThat(failure.args()[0]).isEqualTo("FAILED");
        assertThat(failure.args()[5]).isEqualTo(7L);
    }


    @Test
    void failedIndexTaskSchedulesRetryBeforeDeadLetterThreshold() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.retryCount = 2;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                new FailingFileInternalApi(),
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        RecordingQueryOperations.UpdateCall failure = queryOperations.updateCalls.stream()
                .filter(call -> call.sql().contains("set status = ?"))
                .findFirst()
                .orElseThrow();
        assertThat(failure.args()[0]).isEqualTo("FAILED");
        assertThat(failure.args()[2]).isEqualTo(3);
        assertThat(failure.args()[3]).isNotNull();
        assertThat(failure.sql()).contains("index_claim_token = ?", "created_by = ?", "created_by_uuid = ?");
        assertThat(failure.args()).contains(7L, "user-uuid-7");
    }

    @Test
    void failedIndexTaskMovesToDeadLetterAtRetryThreshold() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.retryCount = 4;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                new FailingFileInternalApi(),
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        RecordingQueryOperations.UpdateCall failure = queryOperations.updateCalls.stream()
                .filter(call -> call.sql().contains("set status = ?"))
                .findFirst()
                .orElseThrow();
        assertThat(failure.args()[0]).isEqualTo("DEAD_LETTER");
        assertThat(failure.args()[2]).isEqualTo(5);
        assertThat(failure.args()[3]).isNull();
        assertThat(failure.sql()).contains("index_claim_token = ?", "created_by = ?", "created_by_uuid = ?");
        assertThat(failure.args()).contains(7L, "user-uuid-7");
    }

    @Test
    void retrieve_shouldUseVectorProjectionToRankBoundedCandidates() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.vectorSearchRows = true;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:query")));

        var references = service.retrieve(currentUser, "合同审批", List.of(20L), 2);

        assertThat(queryOperations.lastListSql).contains("embedding_vector_json");
        assertThat(references).hasSize(2);
        assertThat(references.get(0).getContent()).contains("合同审批");
    }

    @Test
    void createKnowledgeBaseShouldUseLastInsertId() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.lastInsertId = 88L;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:create")));
        AiDTO.KnowledgeBaseUpsertRequest request = new AiDTO.KnowledgeBaseUpsertRequest();
        request.setName("研发知识库");
        request.setDescription("研发资料");
        request.setStatus("ENABLED");
        request.setVisibilityScope("PERSONAL");

        var result = service.createKnowledgeBase(currentUser, request);

        assertThat(queryOperations.lastInsertIdQueried).isTrue();
        assertThat(result.getId()).isEqualTo(88L);
        assertThat(result.getName()).isEqualTo("研发知识库");
    }

    @Test
    void createKnowledgeBaseShouldRejectWhenInsertMissesBeforeGeneratedIdLookup() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.knowledgeBaseInsertResult = 0;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:create")));
        AiDTO.KnowledgeBaseUpsertRequest request = new AiDTO.KnowledgeBaseUpsertRequest();
        request.setName("研发知识库");
        request.setDescription("研发资料");
        request.setStatus("ENABLED");
        request.setVisibilityScope("PERSONAL");

        assertThatThrownBy(() -> service.createKnowledgeBase(currentUser, request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Knowledge base changed, please retry");
                });

        assertThat(queryOperations.lastInsertIdQueries).isZero();
    }

    @Test
    void updateKnowledgeBaseShouldBindFinalWriteToOwnerUuid() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:manage")));
        AiDTO.KnowledgeBaseUpsertRequest request = new AiDTO.KnowledgeBaseUpsertRequest();
        request.setName("Research KB");
        request.setStatus("ENABLED");
        request.setVisibilityScope("PERSONAL");

        service.updateKnowledgeBase(currentUser, 88L, request);

        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("update ai_knowledge_base", "owner_user_id = ?", "owner_user_uuid = ?");
            assertThat(call.args()).contains(88L, 7L, "user-uuid-7");
        });
    }

    @Test
    void updateKnowledgeBaseShouldRejectWhenFinalWriteMisses() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.knowledgeBaseUpdateResult = 0;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:manage")));
        AiDTO.KnowledgeBaseUpsertRequest request = new AiDTO.KnowledgeBaseUpsertRequest();
        request.setName("Research KB");
        request.setStatus("ENABLED");
        request.setVisibilityScope("PERSONAL");

        assertThatThrownBy(() -> service.updateKnowledgeBase(currentUser, 88L, request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Knowledge base changed, please retry");
                });
    }

    @Test
    void deleteKnowledgeBaseShouldBindCascadeDeletesToOwnerUuid() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:manage")));

        assertThat(service.deleteKnowledgeBase(currentUser, 88L)).isTrue();

        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("update ai_knowledge_base", "owner_user_id = ?", "owner_user_uuid = ?");
            assertThat(call.args()).contains(88L, 7L, "user-uuid-7");
        });
        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("update ai_knowledge_document", "exists", "owner_user_id = ?", "owner_user_uuid = ?");
            assertThat(call.args()).contains(88L, 7L, "user-uuid-7");
        });
        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("update ai_knowledge_chunk", "exists", "owner_user_id = ?", "owner_user_uuid = ?");
            assertThat(call.args()).contains(88L, 7L, "user-uuid-7");
        });
        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("update ai_employee_knowledge_base", "exists", "owner_user_id = ?", "owner_user_uuid = ?");
            assertThat(call.args()).contains(88L, 7L, "user-uuid-7");
        });
        assertThat(queryOperations.updateCalls).anySatisfy(call -> {
            assertThat(call.sql()).contains("update ai_knowledge_base_acl", "exists", "owner_user_id = ?", "owner_user_uuid = ?");
            assertThat(call.args()).contains(88L, 7L, "user-uuid-7");
        });
    }

    @Test
    void deleteKnowledgeBaseShouldRejectWhenBaseDeleteMisses() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.knowledgeBaseDeleteResult = 0;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = trusted(new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:manage")));

        assertThatThrownBy(() -> service.deleteKnowledgeBase(currentUser, 88L))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Knowledge base changed, please retry");
                });
    }

    private static AiKnowledgeVectorService vectorService() {
        return new AiKnowledgeVectorService(new LocalHashingAiEmbeddingModel());
    }

    private static CurrentUser unauthenticatedUser() {
        return new CurrentUser(7L, "admin", 1L, "session", 1, false, Set.of("*", "ai:knowledge:view", "ai:knowledge:create"));
    }

    private static CurrentUser blankUsernameUser() {
        return new CurrentUser(7L, " ", 1L, "session", 1, true, Set.of("*", "ai:knowledge:view", "ai:knowledge:create"));
    }

    private static CurrentUser missingSessionVersionUser() {
        return new CurrentUser(7L, "admin", 1L, "session", null, true, Set.of("*", "ai:knowledge:view", "ai:knowledge:create"));
    }

    private static CurrentUser trusted(CurrentUser currentUser) {
        currentUser.setUserUuid("user-uuid-" + currentUser.getUserId());
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private String lastListSql = "";
        private int retryCount = 0;
        private Long createdBy = 7L;
        private String createdByUserUuid = "user-uuid-7";
        private boolean vectorSearchRows;
        private boolean lastInsertIdQueried;
        private int lastInsertIdQueries;
        private int documentIdLookupQueries;
        private Long lastInsertId = 0L;
        private int countQueryCount;
        private boolean queryCalled;
        private boolean existsCalled;
        private Integer chunkInsertResult;
        private Integer knowledgeBaseInsertResult;
        private Integer knowledgeBaseUpdateResult;
        private Integer knowledgeBaseDeleteResult;
        private Integer knowledgeDocumentInsertResult;
        private Integer knowledgeDocumentDeleteResult;
        private Integer knowledgeDocumentIndexingResult;

        @Override
        public boolean exists(String sql, Object... args) {
            existsCalled = true;
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("select last_insert_id()")) {
                lastInsertIdQueried = true;
                lastInsertIdQueries += 1;
                return (T) lastInsertId;
            }
            if (sql.contains("select id from ai_knowledge_document")) {
                documentIdLookupQueries += 1;
                return (T) Long.valueOf(30L);
            }
            if (sql.contains("count(1)")) {
                countQueryCount += 1;
            }
            if (requiredType == Long.class) {
                return (T) Long.valueOf(0L);
            }
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            List<T> rows = query(sql, rowMapper, args);
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCalled = true;
            this.lastListSql = sql;
            if (sql.contains("from ai_knowledge_document")) {
                try {
                    return List.of(rowMapper.mapRow(new SqlRow(documentRow()), 0));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            if (vectorSearchRows && sql.contains("from ai_knowledge_chunk")) {
                AiKnowledgeVectorService vectorService = vectorService();
                try {
                    return List.of(
                            rowMapper.mapRow(new SqlRow(Map.of(
                                    "chunk_id", 301L,
                                    "knowledge_base_id", 20L,
                                    "knowledge_base_name", "合同知识库",
                                    "document_id", 30L,
                                    "document_title", "合同审批制度",
                                    "file_id", 40L,
                                    "original_file_name", "contract.txt",
                                    "chunk_index", 0,
                                    "content", "合同审批需要法务和财务共同确认",
                                    "embedding_vector_json", vectorService.project("合同审批需要法务和财务共同确认").vectorJson()
                            )), 0),
                            rowMapper.mapRow(new SqlRow(Map.of(
                                    "chunk_id", 302L,
                                    "knowledge_base_id", 20L,
                                    "knowledge_base_name", "通用知识库",
                                    "document_id", 31L,
                                    "document_title", "假期制度",
                                    "file_id", 41L,
                                    "original_file_name", "holiday.txt",
                                    "chunk_index", 0,
                                    "content", "员工假期申请需要提前提交",
                                    "embedding_vector_json", vectorService.project("员工假期申请需要提前提交").vectorJson()
                            )), 1)
                    );
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            if (sql.contains("from ai_employee")) {
                try {
                    return List.of(rowMapper.mapRow(new SqlRow(Map.of(
                            "id", 11L,
                            "username", "assistant",
                            "enabled", true
                    )), 0));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            if (sql.contains("from ai_knowledge_base")) {
                try {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", 88L);
                    row.put("kb_code", "kb_88");
                    row.put("name", "研发知识库");
                    row.put("description", "研发资料");
                    row.put("status", "ENABLED");
                    row.put("visibility_scope", "PERSONAL");
                    row.put("owner_user_id", 7L);
                    row.put("owner_user_uuid", "user-uuid-7");
                    row.put("created_by", 7L);
                    row.put("document_count", 0L);
                    row.put("chunk_count", 0L);
                    row.put("create_time", LocalDateTime.now());
                    row.put("update_time", LocalDateTime.now());
                    return List.of(rowMapper.mapRow(new SqlRow(row), 0));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            return List.of();
        }

        private Map<String, Object> documentRow() {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", 30L);
            row.put("knowledge_base_id", 20L);
            row.put("file_id", 40L);
            row.put("title", "doc.txt");
            row.put("original_file_name", "doc.txt");
            row.put("file_extension", "txt");
            row.put("mime_type", "text/plain");
            row.put("file_size_bytes", 21L);
            row.put("status", "READY");
            row.put("parse_error", null);
            row.put("extracted_char_count", 21);
            row.put("chunk_count", 1);
            row.put("created_by", createdBy);
            row.put("created_by_user_uuid", createdByUserUuid);
            row.put("created_by_username", "admin");
            row.put("index_retry_count", retryCount);
            row.put("create_time", LocalDateTime.now());
            row.put("update_time", LocalDateTime.now());
            return row;
        }

        private final List<String> updateSql = new ArrayList<>();
        private final List<UpdateCall> updateCalls = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updateSql.add(sql);
            updateCalls.add(new UpdateCall(sql, args));
            if (sql.contains("insert into ai_knowledge_base")) {
                return knowledgeBaseInsertResult == null ? 1 : knowledgeBaseInsertResult;
            }
            if (sql.contains("update ai_knowledge_base") && sql.contains("set name = ?")) {
                return knowledgeBaseUpdateResult == null ? 1 : knowledgeBaseUpdateResult;
            }
            if (sql.contains("update ai_knowledge_base") && sql.contains("set is_deleted = 1")) {
                return knowledgeBaseDeleteResult == null ? 1 : knowledgeBaseDeleteResult;
            }
            if (sql.contains("insert into ai_knowledge_document")) {
                return knowledgeDocumentInsertResult == null ? 1 : knowledgeDocumentInsertResult;
            }
            if (sql.contains("update ai_knowledge_document") && sql.contains("set is_deleted = 1")) {
                return knowledgeDocumentDeleteResult == null ? 1 : knowledgeDocumentDeleteResult;
            }
            if (sql.contains("update ai_knowledge_document") && sql.contains("set status = 'INDEXING'")) {
                return knowledgeDocumentIndexingResult == null ? 1 : knowledgeDocumentIndexingResult;
            }
            if (sql.contains("insert into ai_knowledge_chunk")) {
                return chunkInsertResult == null ? Math.max(1, args.length / 15) : chunkInsertResult;
            }
            return 1;
        }

        private record UpdateCall(String sql, Object[] args) {
        }
    }

    private static final class InMemoryFileInternalApi implements FileInternalApi {
        private final byte[] content;
        private String textArtifact;
        private int contentReadCount;
        private Long uploadedForUserId;
        private String uploadedForUserUuid;
        private String uploadedForUsername;
        private Long lastReadUserId;
        private String lastReadUserUuid;
        private String lastReadUsername;

        private InMemoryFileInternalApi(byte[] content) {
            this.content = content;
        }

        @Override
        public FileObjectDTO uploadImage(MultipartFile file, String category, String remark, String bucket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObjectDTO uploadDocument(MultipartFile file, String category, String tags, String remark, String bucket) {
            throw new AssertionError("knowledge documents must be uploaded with the acting user identity");
        }

        @Override
        public FileObjectDTO uploadDocumentForUser(
                MultipartFile file,
                String category,
                String tags,
                String remark,
                String bucket,
                Long userId,
                String userUuid,
                String username
        ) {
            this.uploadedForUserId = userId;
            this.uploadedForUserUuid = userUuid;
            this.uploadedForUsername = username;
            return new FileObjectDTO(
                    40L,
                    userId,
                    userUuid,
                    username,
                    file.getOriginalFilename(),
                    "doc.txt",
                    "LOCAL",
                    bucket,
                    "txt",
                    file.getContentType(),
                    (long) content.length,
                    content.length + " B",
                    "storage/uploads/doc.txt",
                    "/api/uploads/doc.txt",
                    null,
                    "/api/uploads/doc.txt",
                    "TEXT",
                    true,
                    category,
                    tags,
                    remark,
                    "READY",
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
        }

        @Override
        public FileContentDTO readFileContentForUser(Long fileId, Long userId, String userUuid, String username) {
            contentReadCount++;
            lastReadUserId = userId;
            lastReadUserUuid = userUuid;
            lastReadUsername = username;
            return new FileContentDTO(fileId, "doc.txt", "text/plain", "txt", content);
        }

        @Override
        public FileProcessingArtifactDTO readProcessingArtifactForUser(Long fileId, Long userId, String userUuid, String username, String artifactType) {
            lastReadUserId = userId;
            lastReadUserUuid = userUuid;
            lastReadUsername = username;
            if (textArtifact == null) {
                throw new RuntimeException("artifact unavailable");
            }
            return new FileProcessingArtifactDTO(
                    9001L,
                    fileId,
                    "TEXT_EXTRACT",
                    artifactType,
                    null,
                    textArtifact,
                    textArtifact.length(),
                    LocalDateTime.now()
            );
        }
    }

    private static final class FailingFileInternalApi implements FileInternalApi {

        @Override
        public FileObjectDTO uploadImage(MultipartFile file, String category, String remark, String bucket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObjectDTO uploadDocument(MultipartFile file, String category, String tags, String remark, String bucket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileContentDTO readFileContentForUser(Long fileId, Long userId, String userUuid, String username) {
            throw new RuntimeException("temporary parser failure");
        }
    }

    private record TestMultipartFile(String originalFilename, String contentType, byte[] bytes) implements MultipartFile {
        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes == null ? 0 : bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes == null ? new byte[0] : bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(getBytes());
        }

        @Override
        public void transferTo(File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), getBytes());
        }
    }
}
