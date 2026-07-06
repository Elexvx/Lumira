package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AiConversationService {

    Long ensureConversation(Long ownerUserId, String ownerUserUuid, Long employeeId, Long conversationId, String title);

    Long recordMessage(Long ownerUserId, String ownerUserUuid, Long conversationId, String role, String content);

    void recordMessageAttachments(CurrentUser currentUser, Long conversationId, Long messageId, List<AiDTO.ChatAttachmentItem> attachments);
}

@Service
@Primary
class JdbcAiConversationService implements AiConversationService {
    private static final String STATUS_ENABLED = "ENABLED";
    private static final int MAX_MESSAGE_ATTACHMENTS = 10;

    private final MyBatisQueryOperations jdbcTemplate;
    private final FileInternalApi fileInternalApi;
    private final AiAssistantEmployeeResolver aiAssistantEmployeeResolver;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    JdbcAiConversationService(MyBatisQueryOperations jdbcTemplate, FileInternalApi fileInternalApi) {
        this(jdbcTemplate, fileInternalApi, null, null);
    }

    JdbcAiConversationService(
            MyBatisQueryOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, fileInternalApi, null, sessionAuthenticationService);
    }

    JdbcAiConversationService(
            MyBatisQueryOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileInternalApi = fileInternalApi;
        this.aiAssistantEmployeeResolver = new AiAssistantEmployeeResolver(jdbcTemplate);
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @Override
    @Transactional
    public Long ensureConversation(Long ownerUserId, String ownerUserUuid, Long employeeId, Long conversationId, String title) {
        String trustedOwnerUserUuid = requireUserUuid(ownerUserUuid);
        Long resolvedEmployeeId = employeeId != null && employeeId > 0
                ? employeeId
                : aiAssistantEmployeeResolver.getOrCreateAssistantEmployee().getId();
        if (conversationId != null) {
            Long foundId = jdbcTemplate.query(
                    """
                            select id
                            from ai_conversation
                            where owner_user_id = ?
                              and owner_user_uuid = ?
                              and id = ?
                              and is_deleted = 0
                            limit 1
                            """,
                    (rs, rowNum) -> rs.getLong("id"),
                    ownerUserId,
                    trustedOwnerUserUuid,
                    conversationId
            ).stream().findFirst().orElse(null);
            if (foundId != null) {
                int updated = jdbcTemplate.update(
                        """
                                update ai_conversation
                                set employee_id = ?, update_time = ?
                                where id = ? and owner_user_id = ? and owner_user_uuid = ? and is_deleted = 0
                                """,
                        resolvedEmployeeId,
                        LocalDateTime.now(),
                        foundId,
                        ownerUserId,
                        trustedOwnerUserUuid
                );
                if (updated <= 0) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "Conversation changed, please retry");
                }
                return foundId;
            }
        }

        String resolvedTitle = StringUtils.hasText(title) ? title : "新会话";
        String conversationCode = "conv_" + UUID.randomUUID().toString().replace("-", "");
        int inserted = jdbcTemplate.update(
                """
                        insert into ai_conversation (
                            owner_user_id, owner_user_uuid, employee_id, conversation_code, title, status, latest_message_at, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, 'ACTIVE', null, 0, ?, ?)
                        """,
                ownerUserId,
                trustedOwnerUserUuid,
                resolvedEmployeeId,
                conversationCode,
                resolvedTitle,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        if (inserted <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Conversation changed, please retry");
        }
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    @Transactional
    public Long recordMessage(Long ownerUserId, String ownerUserUuid, Long conversationId, String role, String content) {
        String trustedOwnerUserUuid = requireUserUuid(ownerUserUuid);
        int inserted = jdbcTemplate.update(
                """
                        insert into ai_message (
                            conversation_id, role, content, is_deleted, create_time, update_time
                        )
                        select c.id, ?, ?, 0, ?, ?
                        from ai_conversation c
                        where c.id = ?
                          and c.owner_user_id = ?
                          and c.owner_user_uuid = ?
                          and c.is_deleted = 0
                        """,
                role,
                content,
                LocalDateTime.now(),
                LocalDateTime.now(),
                conversationId,
                ownerUserId,
                trustedOwnerUserUuid
        );
        if (inserted <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Conversation changed, please retry");
        }
        int updated = jdbcTemplate.update(
                """
                        update ai_conversation
                        set latest_message_at = ?, update_time = ?
                        where id = ?
                          and owner_user_id = ?
                          and owner_user_uuid = ?
                          and is_deleted = 0
                        """,
                LocalDateTime.now(),
                LocalDateTime.now(),
                conversationId,
                ownerUserId,
                trustedOwnerUserUuid
        );
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Conversation changed, please retry");
        }
        return jdbcTemplate.queryForObject(
                """
                        select id
                        from ai_message
                        where conversation_id = ?
                          and role = ?
                          and exists (
                              select 1
                              from ai_conversation c
                              where c.id = ai_message.conversation_id
                                and c.owner_user_id = ?
                                and c.owner_user_uuid = ?
                                and c.is_deleted = 0
                          )
                          and is_deleted = 0
                        order by id desc
                        limit 1
                        """,
                Long.class,
                conversationId,
                role,
                ownerUserId,
                trustedOwnerUserUuid
        );
    }

    @Override
    @Transactional
    public void recordMessageAttachments(CurrentUser currentUser, Long conversationId, Long messageId, List<AiDTO.ChatAttachmentItem> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        requirePositiveId(conversationId, "Conversation id is required");
        requirePositiveId(messageId, "Message id is required");
        if (attachments.size() > MAX_MESSAGE_ATTACHMENTS) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Too many message attachments");
        }

        for (AiDTO.ChatAttachmentItem attachment : attachments) {
            if (attachment == null) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Attachment is required");
            }
            Long fileId = requirePositiveId(attachment.getFileId(), "Attachment file id is required");
            FileSnapshot snapshot = queryFileSnapshot(runtimeUser, fileId);
            int updated = jdbcTemplate.update(
                    """
                            insert into ai_message_attachment (
                                conversation_id, message_id, file_id, original_file_name,
                                file_extension, mime_type, file_size_bytes, public_url, preview_url,
                                download_url, preview_mode, is_deleted, create_time, update_time
                            )
                            select c.id, m.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?
                            from ai_message m
                            join ai_conversation c
                              on c.id = m.conversation_id
                             and c.owner_user_id = ?
                             and c.owner_user_uuid = ?
                             and c.is_deleted = 0
                            where c.id = ?
                              and m.id = ?
                              and m.is_deleted = 0
                            on duplicate key update
                                original_file_name = case when exists (
                                    select 1 from ai_message m2
                                    join ai_conversation c2 on c2.id = m2.conversation_id
                                    where m2.id = ai_message_attachment.message_id
                                      and c2.id = ai_message_attachment.conversation_id
                                      and c2.owner_user_id = ?
                                      and c2.owner_user_uuid = ?
                                      and c2.is_deleted = 0
                                      and m2.is_deleted = 0
                                ) then values(original_file_name) else original_file_name end,
                                file_extension = case when exists (
                                    select 1 from ai_message m2
                                    join ai_conversation c2 on c2.id = m2.conversation_id
                                    where m2.id = ai_message_attachment.message_id
                                      and c2.id = ai_message_attachment.conversation_id
                                      and c2.owner_user_id = ?
                                      and c2.owner_user_uuid = ?
                                      and c2.is_deleted = 0
                                      and m2.is_deleted = 0
                                ) then values(file_extension) else file_extension end,
                                mime_type = case when exists (
                                    select 1 from ai_message m2
                                    join ai_conversation c2 on c2.id = m2.conversation_id
                                    where m2.id = ai_message_attachment.message_id
                                      and c2.id = ai_message_attachment.conversation_id
                                      and c2.owner_user_id = ?
                                      and c2.owner_user_uuid = ?
                                      and c2.is_deleted = 0
                                      and m2.is_deleted = 0
                                ) then values(mime_type) else mime_type end,
                                file_size_bytes = case when exists (
                                    select 1 from ai_message m2
                                    join ai_conversation c2 on c2.id = m2.conversation_id
                                    where m2.id = ai_message_attachment.message_id
                                      and c2.id = ai_message_attachment.conversation_id
                                      and c2.owner_user_id = ?
                                      and c2.owner_user_uuid = ?
                                      and c2.is_deleted = 0
                                      and m2.is_deleted = 0
                                ) then values(file_size_bytes) else file_size_bytes end,
                                public_url = case when exists (
                                    select 1 from ai_message m2
                                    join ai_conversation c2 on c2.id = m2.conversation_id
                                    where m2.id = ai_message_attachment.message_id
                                      and c2.id = ai_message_attachment.conversation_id
                                      and c2.owner_user_id = ?
                                      and c2.owner_user_uuid = ?
                                      and c2.is_deleted = 0
                                      and m2.is_deleted = 0
                                ) then values(public_url) else public_url end,
                                preview_url = case when exists (
                                    select 1 from ai_message m2
                                    join ai_conversation c2 on c2.id = m2.conversation_id
                                    where m2.id = ai_message_attachment.message_id
                                      and c2.id = ai_message_attachment.conversation_id
                                      and c2.owner_user_id = ?
                                      and c2.owner_user_uuid = ?
                                      and c2.is_deleted = 0
                                      and m2.is_deleted = 0
                                ) then values(preview_url) else preview_url end,
                                download_url = case when exists (
                                    select 1 from ai_message m2
                                    join ai_conversation c2 on c2.id = m2.conversation_id
                                    where m2.id = ai_message_attachment.message_id
                                      and c2.id = ai_message_attachment.conversation_id
                                      and c2.owner_user_id = ?
                                      and c2.owner_user_uuid = ?
                                      and c2.is_deleted = 0
                                      and m2.is_deleted = 0
                                ) then values(download_url) else download_url end,
                                preview_mode = case when exists (
                                    select 1 from ai_message m2
                                    join ai_conversation c2 on c2.id = m2.conversation_id
                                    where m2.id = ai_message_attachment.message_id
                                      and c2.id = ai_message_attachment.conversation_id
                                      and c2.owner_user_id = ?
                                      and c2.owner_user_uuid = ?
                                      and c2.is_deleted = 0
                                      and m2.is_deleted = 0
                                ) then values(preview_mode) else preview_mode end,
                                is_deleted = case when exists (
                                    select 1 from ai_message m2
                                    join ai_conversation c2 on c2.id = m2.conversation_id
                                    where m2.id = ai_message_attachment.message_id
                                      and c2.id = ai_message_attachment.conversation_id
                                      and c2.owner_user_id = ?
                                      and c2.owner_user_uuid = ?
                                      and c2.is_deleted = 0
                                      and m2.is_deleted = 0
                                ) then 0 else is_deleted end,
                                update_time = case when exists (
                                    select 1 from ai_message m2
                                    join ai_conversation c2 on c2.id = m2.conversation_id
                                    where m2.id = ai_message_attachment.message_id
                                      and c2.id = ai_message_attachment.conversation_id
                                      and c2.owner_user_id = ?
                                      and c2.owner_user_uuid = ?
                                      and c2.is_deleted = 0
                                      and m2.is_deleted = 0
                                ) then values(update_time) else update_time end
                            """,
                    snapshot.fileId(),
                    snapshot.originalFileName(),
                    snapshot.fileExtension(),
                    snapshot.mimeType(),
                    snapshot.fileSizeBytes(),
                    snapshot.publicUrl(),
                    snapshot.previewUrl(),
                    snapshot.downloadUrl(),
                    snapshot.previewMode(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid(),
                    conversationId,
                    messageId,
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid(),
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid(),
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid(),
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid(),
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid(),
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid(),
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid(),
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid(),
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid(),
                    runtimeUser.getUserId(),
                    runtimeUser.getUserUuid()
            );
            if (updated <= 0) {
                throw new BizException(ErrorCode.BIZ_ERROR, "Message attachment changed, please retry");
            }
        }
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return currentUser;
        }
        if (systemInternalApi != null) {
            Long userId = currentUser.getUserId();
            String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
            if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
                throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
            }
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user is disabled or no longer active");
            }
            currentUser.setUserId(userSnapshot.userId());
            currentUser.setUserUuid(userSnapshot.userUuid().trim());
            currentUser.setUsername(userSnapshot.username());
        }
        return currentUser;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
        }
        return refreshedUser;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions());
        target.setRoleIds(source.getRoleIds());
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds());
        target.setDescendantDeptIds(source.getDescendantDeptIds());
        target.setDataScopes(source.getDataScopes());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(source.getSimulatedRoleId());
        target.setLoginType(source.getLoginType());
    }

    private FileSnapshot queryFileSnapshot(CurrentUser currentUser, Long fileId) {
        Long trustedFileId = requirePositiveId(fileId, "Attachment file id is required");
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
        }
        FileObjectDTO file = fileInternalApi.getFileForUser(
                trustedFileId,
                trustedUserId(currentUser),
                trustedUserUuid(currentUser),
                trustedUsername(currentUser),
                false,
                false
        );
        if (file == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件文件不存在");
        }
        return new FileSnapshot(
                file.id(),
                file.originalFileName(),
                file.fileExtension(),
                file.mimeType(),
                file.fileSizeBytes(),
                file.publicUrl(),
                StringUtils.hasText(file.previewUrl()) ? file.previewUrl() : file.publicUrl(),
                StringUtils.hasText(file.downloadUrl()) ? file.downloadUrl() : file.publicUrl(),
                file.previewMode()
        );
    }

    private Long trustedUserId(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
        }
        return currentUser.getUserId();
    }

    private String trustedUsername(CurrentUser currentUser) {
        trustedUserId(currentUser);
        return currentUser.getUsername();
    }

    private String trustedUserUuid(CurrentUser currentUser) {
        trustedUserId(currentUser);
        return currentUser.getUserUuid();
    }

    private Long requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
        return id;
    }

    private String requireUserUuid(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Conversation owner uuid is required");
        }
        return userUuid.trim();
    }

    private record FileSnapshot(
            Long fileId,
            String originalFileName,
            String fileExtension,
            String mimeType,
            Long fileSizeBytes,
            String publicUrl,
            String previewUrl,
            String downloadUrl,
            String previewMode
    ) {
    }
}
