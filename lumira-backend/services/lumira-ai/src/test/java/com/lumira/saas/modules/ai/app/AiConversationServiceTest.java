package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.RowMapper;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.integration.AiTrustedSessionResolver;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.infrastructure.JdbcAiConversationPersistenceRepository;
import com.lumira.saas.modules.ai.repository.AiConversationPersistenceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiConversationServiceTest {

    @Test
    void ensureConversationPersistsOwnerUuidForNewConversation() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);

        Long conversationId = service.ensureConversation(1001L, "user-uuid-1001", 1L, null, "hello");

        assertThat(conversationId).isEqualTo(99L);
        assertThat(jdbcTemplate.lastUpdateSql).contains("owner_user_uuid");
        assertThat(jdbcTemplate.lastUpdateArgs).containsSequence(1001L, "user-uuid-1001", 1L);
    }

    @Test
    void ensureConversationRejectsWhenInsertMissesBeforeGeneratedIdLookup() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        jdbcTemplate.updateCount = 0;
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);

        assertThatThrownBy(() -> service.ensureConversation(1001L, "user-uuid-1001", 1L, null, "hello"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Conversation changed, please retry");
                });

        assertThat(jdbcTemplate.lastInsertIdQueries).isZero();
    }

    @Test
    void ensureConversationRequiresOwnerUuidForExistingConversationLookup() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);

        assertThatThrownBy(() -> service.ensureConversation(1001L, " ", 1L, 10L, "hello"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void recordMessageBindsConversationToOwnerUuid() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);

        Long messageId = service.recordMessage(1001L, "user-uuid-1001", 10L, "USER", "hello");

        assertThat(messageId).isEqualTo(99L);
        assertThat(jdbcTemplate.updateSqls).allSatisfy(sql -> assertThat(sql).contains("owner_user_uuid"));
        assertThat(jdbcTemplate.lastQueryForObjectSql).contains("owner_user_uuid");
        assertThat(jdbcTemplate.lastQueryForObjectArgs).contains(1001L, "user-uuid-1001");
    }

    @Test
    void conversationWritesShouldCheckFinalUpdateCounts() throws Exception {
        String applicationSource = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/lumira/saas/modules/ai/app/AiConversationService.java"));
        String repositorySource = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/lumira/saas/modules/ai/infrastructure/JdbcAiConversationPersistenceRepository.java"));

        assertThat(applicationSource)
                .contains("Conversation changed, please retry")
                .contains("Message attachment changed, please retry");
        assertThat(repositorySource)
                .contains("int inserted = database.update")
                .contains("if (inserted != 1)")
                .contains("if (updated != 1)");
    }

    @Test
    void messageAttachmentsReadFilesAsCurrentUserOnly() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);

        when(fileInternalApi.getFileForUser(2001L, 1001L, "user-uuid-1001", "alice", false, false, null))
                .thenReturn(file(2001L));
        service.recordMessageAttachments(currentUser(), 10L, 20L, List.of(attachment));

        verify(fileInternalApi).getFileForUser(2001L, 1001L, "user-uuid-1001", "alice", false, false, null);
    }

    @Test
    void messageAttachmentWritesShouldBindConversationMessageAndOwnerUuid() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);

        when(fileInternalApi.getFileForUser(2001L, 1001L, "user-uuid-1001", "alice", false, false, null))
                .thenReturn(file(2001L));

        service.recordMessageAttachments(currentUser(), 10L, 20L, List.of(attachment));

        assertThat(jdbcTemplate.lastUpdateSql)
                .contains("from ai_message m")
                .contains("join ai_conversation c")
                .contains("c.owner_user_id = ?")
                .contains("c.owner_user_uuid = ?")
                .contains("m.id = ?")
                .contains("is_deleted = case when exists")
                .contains("c2.owner_user_id = ?")
                .contains("c2.owner_user_uuid = ?")
                .doesNotContain("is_deleted = 0,");
        assertThat(jdbcTemplate.lastUpdateArgs).contains(1001L, "user-uuid-1001", 10L, 20L);
    }

    @Test
    void messageAttachmentsRejectMissingCurrentUser() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);

        assertThatThrownBy(() -> service.recordMessageAttachments(null, 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void messageAttachmentsRejectUnauthenticatedCurrentUserBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);

        assertThatThrownBy(() -> service.recordMessageAttachments(unauthenticatedUser(), 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRejectRevokedSessionTicketBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        AiTrustedSessionResolver sessionAuthenticationService = mock(AiTrustedSessionResolver.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate),
                fileInternalApi,
                sessionAuthenticationService
        );
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser(), 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRejectTrustedUserWhenNoTrustedResolverIsAvailableBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate),
                fileInternalApi,
                null,
                null
        );
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser(), 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRejectDisabledTrustedIdentityBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate),
                fileInternalApi,
                systemInternalApi,
                null
        );
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "DISABLED"));

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser(), 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRejectTrustedIdentityWhenLiveUsernameIsUnavailableBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate),
                fileInternalApi,
                systemInternalApi,
                null
        );
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser(), 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRefreshLiveUsernameBeforeFileLookup() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate),
                fileInternalApi,
                systemInternalApi,
                null
        );
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername("alice-stale");
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "ENABLED"));
        when(systemInternalApi.permissionSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(permissionSnapshot("ai:chat:send"));
        when(fileInternalApi.getFileForUser(2001L, 1001L, "user-uuid-1001", "alice-live", false, false, null))
                .thenReturn(file(2001L));

        service.recordMessageAttachments(currentUser, 10L, 20L, List.of(attachment));

        verify(fileInternalApi).getFileForUser(2001L, 1001L, "user-uuid-1001", "alice-live", false, false, null);
        assertThat(currentUser.getUsername()).isEqualTo("alice-live");
    }

    @Test
    void messageAttachmentsShouldNormalizeInvalidSimulatedRoleIdBeforeFileLookup() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate),
                fileInternalApi,
                systemInternalApi,
                null
        );
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(0L);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "ENABLED"));
        when(systemInternalApi.permissionSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(permissionSnapshot("ai:chat:send"));
        when(fileInternalApi.getFileForUser(2001L, 1001L, "user-uuid-1001", "alice-live", false, false, null))
                .thenReturn(file(2001L));

        service.recordMessageAttachments(currentUser, 10L, 20L, List.of(attachment));

        verify(fileInternalApi).getFileForUser(2001L, 1001L, "user-uuid-1001", "alice-live", false, false, null);
        assertThat(currentUser.getSimulatedRoleId()).isNull();
    }

    @Test
    void messageAttachmentsRejectMissingSessionVersionBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser, 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRejectMissingUserUuidBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);
        CurrentUser currentUser = currentUser();
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser, 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRejectMissingPermissionsVersionBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion(" ");

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser, 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRejectWhenLivePermissionsLoseChatPermissionBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate),
                fileInternalApi,
                systemInternalApi,
                null
        );
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "ENABLED"));
        when(systemInternalApi.permissionSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(permissionSnapshot("ai:knowledge:view"));

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser(), 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRejectInvalidIdsBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(2001L);

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser(), 0L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRejectInvalidFileIdBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);
        AiDTO.ChatAttachmentItem attachment = new AiDTO.ChatAttachmentItem();
        attachment.setFileId(0L);

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser(), 10L, 20L, List.of(attachment)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void messageAttachmentsRejectTooManyAttachmentsBeforeFileLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        JdbcAiConversationService service = new JdbcAiConversationService(conversationRepository(jdbcTemplate), fileInternalApi);
        List<AiDTO.ChatAttachmentItem> attachments = IntStream.range(0, 11)
                .mapToObj(index -> {
                    AiDTO.ChatAttachmentItem item = new AiDTO.ChatAttachmentItem();
                    item.setFileId(2000L + index);
                    return item;
                })
                .toList();

        assertThatThrownBy(() -> service.recordMessageAttachments(currentUser(), 10L, 20L, attachments))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(fileInternalApi);
        verifyNoInteractions(jdbcTemplate);
    }

    private CurrentUser currentUser() {
        return trusted(new CurrentUser(1001L, "alice", 100L, "session-1", 1, true, Set.of("ai:chat:send")));
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(1001L, "alice", 100L, "session-1", 1, false, Set.of("*"));
    }

    private CurrentUser trusted(CurrentUser currentUser) {
        currentUser.setUserUuid("user-uuid-" + currentUser.getUserId());
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private FileObjectDTO file(Long fileId) {
        return new FileObjectDTO(
                fileId,
                1001L,
                "user-uuid-1001",
                "alice",
                "report.txt",
                "stored.txt",
                "LOCAL",
                null,
                "txt",
                "text/plain",
                128L,
                "128 B",
                "/data/stored.txt",
                "/files/report.txt",
                null,
                null,
                "TEXT",
                true,
                "ai",
                null,
                null,
                "ENABLED",
                null,
                null
        );
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

    private static PermissionSnapshotDTO permissionSnapshot(String... permissions) {
        return new PermissionSnapshotDTO(
                "permissions-2",
                List.of(permissions),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    private static AiConversationPersistenceRepository conversationRepository(MyBatisQueryOperations database) {
        return new JdbcAiConversationPersistenceRepository(database);
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private String lastUpdateSql;
        private Object[] lastUpdateArgs;
        private final List<String> updateSqls = new java.util.ArrayList<>();
        private String lastQueryForObjectSql;
        private Object[] lastQueryForObjectArgs;
        private int updateCount = 1;
        private int lastInsertIdQueries;

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            this.lastUpdateArgs = args;
            this.updateSqls.add(sql);
            return updateCount;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.lastQueryForObjectSql = sql;
            this.lastQueryForObjectArgs = args;
            if (sql.contains("last_insert_id")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(99L);
            }
            if (sql.contains("from ai_message")) {
                return requiredType.cast(99L);
            }
            return null;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return List.of();
        }
    }
}
