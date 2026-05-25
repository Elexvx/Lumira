package com.legendary.invention.saas.modules.ai.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
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

    JdbcAiConversationService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
                              and ((? is null and employee_id is null) or employee_id = ?)
                              and id = ?
                              and is_deleted = 0
                            limit 1
                            """,
                    (rs, rowNum) -> rs.getLong("id"),
                    tenantId,
                    ownerUserId,
                    employeeId,
                    employeeId,
                    conversationId
            ).stream().findFirst().orElse(null);
            if (foundId != null) {
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
        return jdbcTemplate.queryForObject(
                "select id from ai_conversation where tenant_id = ? and conversation_code = ? and is_deleted = 0 limit 1",
                Long.class,
                tenantId,
                conversationCode
        );
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
        FileSnapshot snapshot = jdbcTemplate.query(
                """
                        select
                            id as fileId,
                            original_filename as originalFileName,
                            file_extension as fileExtension,
                            content_type as mimeType,
                            file_size as fileSizeBytes,
                            public_url as publicUrl,
                            public_url as previewUrl,
                            public_url as downloadUrl,
                            preview_mode as previewMode
                        from file_object
                        where tenant_id = ?
                          and id = ?
                          and deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new FileSnapshot(
                        rs.getLong("fileId"),
                        rs.getString("originalFileName"),
                        rs.getString("fileExtension"),
                        rs.getString("mimeType"),
                        rs.getLong("fileSizeBytes"),
                        rs.getString("publicUrl"),
                        rs.getString("previewUrl"),
                        rs.getString("downloadUrl"),
                        rs.getString("previewMode")
                ),
                tenantId,
                fileId
        ).stream().findFirst().orElse(null);
        if (snapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件文件不存在");
        }
        return snapshot;
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
