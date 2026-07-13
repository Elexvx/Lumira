package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiConversationReadRepository;
import com.lumira.ai.vo.AiConversationVO;
import com.lumira.ai.vo.AiMessageAttachmentVO;
import com.lumira.ai.vo.AiMessageVO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiConversationReadRepository implements AiConversationReadRepository {

    private final JdbcTemplate database;

    public JdbcAiConversationReadRepository(JdbcTemplate database) {
        this.database = database;
    }

    @Override
    public List<AiConversationVO> findConversations(Long ownerUserId, String ownerUserUuid, Long employeeId, long limit, long offset) {
        return database.query("""
                select c.id, c.employee_id, c.owner_user_id,
                       coalesce(e.nickname, e.username, 'AI 助手') as employee_name,
                       c.conversation_code, c.title, c.status, c.is_pinned,
                       (select m.content from ai_message m where m.conversation_id = c.id and m.is_deleted = 0 order by m.id desc limit 1) as preview,
                       c.latest_message_at, c.create_time, c.update_time
                from ai_conversation c
                left join ai_employee e on e.id = c.employee_id and e.is_deleted = 0
                where c.owner_user_id = ? and c.owner_user_uuid = ?
                  and (? is null or c.employee_id = ?) and c.is_deleted = 0
                order by c.is_pinned desc, coalesce(c.latest_message_at, c.create_time) desc, c.id desc
                limit ? offset ?
                """, this::mapConversation, ownerUserId, ownerUserUuid, employeeId, employeeId, limit, offset);
    }

    @Override
    public boolean existsOwnedConversation(Long ownerUserId, String ownerUserUuid, Long conversationId) {
        return !database.queryForList("""
                select 1 from ai_conversation
                where owner_user_id = ? and owner_user_uuid = ? and id = ? and is_deleted = 0
                limit 1
                """, ownerUserId, ownerUserUuid, conversationId).isEmpty();
    }

    @Override
    public List<AiMessageVO> findMessages(Long conversationId, int limit) {
        return database.query("""
                select id, conversation_id, role, content, create_time
                from ai_message where conversation_id = ? and is_deleted = 0
                order by id asc limit ?
                """, (rs, rowNum) -> new AiMessageVO(
                rs.getLong("id"), rs.getLong("conversation_id"), rs.getString("role"),
                rs.getString("content"), List.of(), localDateTime(rs, "create_time")
        ), conversationId, limit);
    }

    @Override
    public Map<Long, List<AiMessageAttachmentVO>> findAttachmentsByMessage(Long conversationId) {
        Map<Long, List<AiMessageAttachmentVO>> result = new LinkedHashMap<>();
        database.query("""
                select id, file_id, message_id, original_file_name, file_extension, mime_type,
                       file_size_bytes, concat(round(coalesce(file_size_bytes, 0) / 1024, 1), ' KB') as file_size_label,
                       public_url, preview_url, download_url, preview_mode
                from ai_message_attachment
                where conversation_id = ? and is_deleted = 0
                order by id asc
                """, (rs, rowNum) -> {
            AiMessageAttachmentVO attachment = new AiMessageAttachmentVO(
                    rs.getLong("id"), objectLong(rs, "file_id"), rs.getString("original_file_name"),
                    rs.getString("file_extension"), rs.getString("mime_type"), objectLong(rs, "file_size_bytes"),
                    rs.getString("file_size_label"), rs.getString("public_url"), rs.getString("preview_url"),
                    rs.getString("download_url"), rs.getString("preview_mode"));
            result.computeIfAbsent(rs.getLong("message_id"), ignored -> new ArrayList<>()).add(attachment);
            return attachment;
        }, conversationId);
        return result;
    }

    private AiConversationVO mapConversation(ResultSet rs, int rowNum) throws SQLException {
        return new AiConversationVO(
                rs.getLong("id"), rs.getLong("employee_id"), rs.getLong("owner_user_id"),
                rs.getString("employee_name"), rs.getString("conversation_code"), rs.getString("title"),
                rs.getString("preview"), rs.getString("status"), rs.getBoolean("is_pinned"),
                localDateTime(rs, "latest_message_at"), localDateTime(rs, "create_time"), localDateTime(rs, "update_time"));
    }

    private Long objectLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
