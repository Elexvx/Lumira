package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.repository.AiConversationPersistenceRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiConversationPersistenceRepository implements AiConversationPersistenceRepository {

    private final MyBatisQueryOperations database;

    public JdbcAiConversationPersistenceRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public Optional<Long> findOwnedConversationId(Long ownerUserId, String ownerUserUuid, Long conversationId) {
        return database.query("""
                select id from ai_conversation
                where owner_user_id = ? and owner_user_uuid = ? and id = ? and is_deleted = 0
                limit 1
                """, (rs, rowNum) -> rs.getLong("id"), ownerUserId, ownerUserUuid, conversationId)
                .stream().findFirst();
    }

    @Override
    public int updateConversationEmployee(
            Long ownerUserId,
            String ownerUserUuid,
            Long conversationId,
            Long employeeId,
            LocalDateTime now
    ) {
        return database.update("""
                update ai_conversation set employee_id = ?, update_time = ?
                where id = ? and owner_user_id = ? and owner_user_uuid = ? and is_deleted = 0
                """, employeeId, now, conversationId, ownerUserId, ownerUserUuid);
    }

    @Override
    public Long createConversation(
            Long ownerUserId,
            String ownerUserUuid,
            Long employeeId,
            String conversationCode,
            String title,
            LocalDateTime now
    ) {
        int inserted = database.update("""
                insert into ai_conversation (
                    owner_user_id, owner_user_uuid, employee_id, conversation_code, title, status,
                    latest_message_at, is_deleted, create_time, update_time
                ) values (?, ?, ?, ?, ?, 'ACTIVE', null, 0, ?, ?)
                """, ownerUserId, ownerUserUuid, employeeId, conversationCode, title, now, now);
        return inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
    }

    @Override
    public Long appendMessage(
            Long ownerUserId,
            String ownerUserUuid,
            Long conversationId,
            String role,
            String content,
            LocalDateTime now
    ) {
        int inserted = database.update("""
                insert into ai_message (conversation_id, role, content, is_deleted, create_time, update_time)
                select c.id, ?, ?, 0, ?, ?
                from ai_conversation c
                where c.id = ? and c.owner_user_id = ? and c.owner_user_uuid = ? and c.is_deleted = 0
                """, role, content, now, now, conversationId, ownerUserId, ownerUserUuid);
        if (inserted != 1) {
            return null;
        }
        int updated = database.update("""
                update ai_conversation set latest_message_at = ?, update_time = ?
                where id = ? and owner_user_id = ? and owner_user_uuid = ? and is_deleted = 0
                """, now, now, conversationId, ownerUserId, ownerUserUuid);
        if (updated != 1) {
            return null;
        }
        return database.queryForObject("""
                select id from ai_message
                where conversation_id = ? and role = ?
                  and exists (
                      select 1 from ai_conversation c
                      where c.id = ai_message.conversation_id and c.owner_user_id = ?
                        and c.owner_user_uuid = ? and c.is_deleted = 0
                  ) and is_deleted = 0
                order by id desc limit 1
                """, Long.class, conversationId, role, ownerUserId, ownerUserUuid);
    }

    @Override
    public int upsertAttachment(AttachmentSnapshot attachment, LocalDateTime now) {
        return database.update("""
                insert into ai_message_attachment (
                    conversation_id, message_id, file_id, original_file_name, file_extension, mime_type,
                    file_size_bytes, public_url, preview_url, download_url, preview_mode,
                    is_deleted, create_time, update_time
                )
                select c.id, m.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?
                from ai_message m
                join ai_conversation c on c.id = m.conversation_id
                    and c.owner_user_id = ? and c.owner_user_uuid = ? and c.is_deleted = 0
                where c.id = ? and m.id = ? and m.is_deleted = 0
                on duplicate key update
                    original_file_name = case when exists (
                        select 1 from ai_message m2 join ai_conversation c2 on c2.id = m2.conversation_id
                        where m2.id = ai_message_attachment.message_id and c2.id = ai_message_attachment.conversation_id
                          and c2.owner_user_id = ? and c2.owner_user_uuid = ? and c2.is_deleted = 0 and m2.is_deleted = 0
                    ) then values(original_file_name) else original_file_name end,
                    file_extension = case when exists (
                        select 1 from ai_message m2 join ai_conversation c2 on c2.id = m2.conversation_id
                        where m2.id = ai_message_attachment.message_id and c2.id = ai_message_attachment.conversation_id
                          and c2.owner_user_id = ? and c2.owner_user_uuid = ? and c2.is_deleted = 0 and m2.is_deleted = 0
                    ) then values(file_extension) else file_extension end,
                    mime_type = case when exists (
                        select 1 from ai_message m2 join ai_conversation c2 on c2.id = m2.conversation_id
                        where m2.id = ai_message_attachment.message_id and c2.id = ai_message_attachment.conversation_id
                          and c2.owner_user_id = ? and c2.owner_user_uuid = ? and c2.is_deleted = 0 and m2.is_deleted = 0
                    ) then values(mime_type) else mime_type end,
                    file_size_bytes = case when exists (
                        select 1 from ai_message m2 join ai_conversation c2 on c2.id = m2.conversation_id
                        where m2.id = ai_message_attachment.message_id and c2.id = ai_message_attachment.conversation_id
                          and c2.owner_user_id = ? and c2.owner_user_uuid = ? and c2.is_deleted = 0 and m2.is_deleted = 0
                    ) then values(file_size_bytes) else file_size_bytes end,
                    public_url = case when exists (
                        select 1 from ai_message m2 join ai_conversation c2 on c2.id = m2.conversation_id
                        where m2.id = ai_message_attachment.message_id and c2.id = ai_message_attachment.conversation_id
                          and c2.owner_user_id = ? and c2.owner_user_uuid = ? and c2.is_deleted = 0 and m2.is_deleted = 0
                    ) then values(public_url) else public_url end,
                    preview_url = case when exists (
                        select 1 from ai_message m2 join ai_conversation c2 on c2.id = m2.conversation_id
                        where m2.id = ai_message_attachment.message_id and c2.id = ai_message_attachment.conversation_id
                          and c2.owner_user_id = ? and c2.owner_user_uuid = ? and c2.is_deleted = 0 and m2.is_deleted = 0
                    ) then values(preview_url) else preview_url end,
                    download_url = case when exists (
                        select 1 from ai_message m2 join ai_conversation c2 on c2.id = m2.conversation_id
                        where m2.id = ai_message_attachment.message_id and c2.id = ai_message_attachment.conversation_id
                          and c2.owner_user_id = ? and c2.owner_user_uuid = ? and c2.is_deleted = 0 and m2.is_deleted = 0
                    ) then values(download_url) else download_url end,
                    preview_mode = case when exists (
                        select 1 from ai_message m2 join ai_conversation c2 on c2.id = m2.conversation_id
                        where m2.id = ai_message_attachment.message_id and c2.id = ai_message_attachment.conversation_id
                          and c2.owner_user_id = ? and c2.owner_user_uuid = ? and c2.is_deleted = 0 and m2.is_deleted = 0
                    ) then values(preview_mode) else preview_mode end,
                    is_deleted = case when exists (
                        select 1 from ai_message m2 join ai_conversation c2 on c2.id = m2.conversation_id
                        where m2.id = ai_message_attachment.message_id and c2.id = ai_message_attachment.conversation_id
                          and c2.owner_user_id = ? and c2.owner_user_uuid = ? and c2.is_deleted = 0 and m2.is_deleted = 0
                    ) then 0 else is_deleted end,
                    update_time = case when exists (
                        select 1 from ai_message m2 join ai_conversation c2 on c2.id = m2.conversation_id
                        where m2.id = ai_message_attachment.message_id and c2.id = ai_message_attachment.conversation_id
                          and c2.owner_user_id = ? and c2.owner_user_uuid = ? and c2.is_deleted = 0 and m2.is_deleted = 0
                    ) then values(update_time) else update_time end
                """,
                attachment.fileId(), attachment.originalFileName(), attachment.fileExtension(), attachment.mimeType(),
                attachment.fileSizeBytes(), attachment.publicUrl(), attachment.previewUrl(), attachment.downloadUrl(),
                attachment.previewMode(), now, now, attachment.ownerUserId(), attachment.ownerUserUuid(),
                attachment.conversationId(), attachment.messageId(),
                attachment.ownerUserId(), attachment.ownerUserUuid(), attachment.ownerUserId(), attachment.ownerUserUuid(),
                attachment.ownerUserId(), attachment.ownerUserUuid(), attachment.ownerUserId(), attachment.ownerUserUuid(),
                attachment.ownerUserId(), attachment.ownerUserUuid(), attachment.ownerUserId(), attachment.ownerUserUuid(),
                attachment.ownerUserId(), attachment.ownerUserUuid(), attachment.ownerUserId(), attachment.ownerUserUuid(),
                attachment.ownerUserId(), attachment.ownerUserUuid(), attachment.ownerUserId(), attachment.ownerUserUuid());
    }
}
