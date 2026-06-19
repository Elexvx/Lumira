package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
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

    Long ensureConversation(Long tenantId, Long ownerUserId, Long employeeId, Long conversationId, String title);

    Long recordMessage(Long tenantId, Long conversationId, String role, String content);

    void recordMessageAttachments(Long tenantId, Long conversationId, Long messageId, List<AiDTO.ChatAttachmentItem> attachments);
}

@Service
@Primary
class JdbcAiConversationService implements AiConversationService {

    private final MyBatisQueryOperations jdbcTemplate;
    private final FileInternalApi fileInternalApi;

    JdbcAiConversationService(MyBatisQueryOperations jdbcTemplate, FileInternalApi fileInternalApi) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileInternalApi = fileInternalApi;
    }

    @Override
    @Transactional
    public Long ensureConversation(Long tenantId, Long ownerUserId, Long employeeId, Long conversationId, String title) {
        if (conversationId != null) {
            Long foundId = jdbcTemplate.query(
                    """
                            select id
                            from ai_conversation
                            where tenant_id = ?
                              and owner_user_id = ?
                              and id = ?
                              and is_deleted = 0
                            limit 1
                            """,
                    (rs, rowNum) -> rs.getLong("id"),
                    tenantId,
                    ownerUserId,
                    conversationId
            ).stream().findFirst().orElse(null);
            if (foundId != null) {
                jdbcTemplate.update(
                        """
                                update ai_conversation
                                set employee_id = ?, update_time = ?
                                where id = ? and tenant_id = ? and owner_user_id = ? and is_deleted = 0
                                """,
                        employeeId,
                        LocalDateTime.now(),
                        foundId,
                        tenantId,
                        ownerUserId
                );
                return foundId;
            }
        }

        String resolvedTitle = StringUtils.hasText(title) ? title : "新会话";
        String conversationCode = "conv_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                """
                        insert into ai_conversation (
                            tenant_id, owner_user_id, employee_id, conversation_code, title, status, latest_message_at, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, 'ACTIVE', null, 0, ?, ?)
                        """,
                tenantId,
                ownerUserId,
                employeeId,
                conversationCode,
                resolvedTitle,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    @Transactional
    public Long recordMessage(Long tenantId, Long conversationId, String role, String content) {
        jdbcTemplate.update(
                """
                        insert into ai_message (
                            tenant_id, conversation_id, role, content, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, 0, ?, ?)
                        """,
                tenantId,
                conversationId,
                role,
                content,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        jdbcTemplate.update(
                """
                        update ai_conversation
                        set latest_message_at = ?, update_time = ?
                        where id = ? and tenant_id = ? and is_deleted = 0
                        """,
                LocalDateTime.now(),
                LocalDateTime.now(),
                conversationId,
                tenantId
        );
        return jdbcTemplate.queryForObject(
                """
                        select id
                        from ai_message
                        where tenant_id = ?
                          and conversation_id = ?
                          and role = ?
                          and is_deleted = 0
                        order by id desc
                        limit 1
                        """,
                Long.class,
                tenantId,
                conversationId,
                role
        );
    }

    @Override
    @Transactional
    public void recordMessageAttachments(Long tenantId, Long conversationId, Long messageId, List<AiDTO.ChatAttachmentItem> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        for (AiDTO.ChatAttachmentItem attachment : attachments) {
            if (attachment == null || attachment.getFileId() == null) {
                continue;
            }

            FileSnapshot snapshot = queryFileSnapshot(tenantId, attachment.getFileId());
            jdbcTemplate.update(
                    """
                            insert into ai_message_attachment (
                                tenant_id, conversation_id, message_id, file_id, original_file_name,
                                file_extension, mime_type, file_size_bytes, public_url, preview_url,
                                download_url, preview_mode, is_deleted, create_time, update_time
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                            on duplicate key update
                                original_file_name = values(original_file_name),
                                file_extension = values(file_extension),
                                mime_type = values(mime_type),
                                file_size_bytes = values(file_size_bytes),
                                public_url = values(public_url),
                                preview_url = values(preview_url),
                                download_url = values(download_url),
                                preview_mode = values(preview_mode),
                                is_deleted = 0,
                                update_time = values(update_time)
                            """,
                    tenantId,
                    conversationId,
                    messageId,
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
                    LocalDateTime.now()
            );
        }
    }

    private FileSnapshot queryFileSnapshot(Long tenantId, Long fileId) {
        FileObjectDTO file = fileInternalApi.getFileForUser(fileId, tenantId, 0L, "ai-conversation", true, false);
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
