package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiConversationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

@Repository
public class JdbcAiConversationRepository extends JdbcAiRepositorySupport implements AiConversationRepository {
    public JdbcAiConversationRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public ConversationIdentity createConversation(Long ownerUserId, Long employeeId, String code, String title, LocalDateTime now) {
        Long id = insertAndReturnId("""
                        insert into ai_conversation (
                            employee_id, owner_user_id, conversation_code, title, status,
                            is_pinned, latest_message_at, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, 'ACTIVE', 0, ?, 0, ?, ?)
                        """,
                ps -> {
                    ps.setLong(1, employeeId);
                    ps.setLong(2, ownerUserId);
                    ps.setString(3, code);
                    ps.setString(4, title);
                    ps.setTimestamp(5, Timestamp.valueOf(now));
                    ps.setTimestamp(6, Timestamp.valueOf(now));
                    ps.setTimestamp(7, Timestamp.valueOf(now));
                });
        return new ConversationIdentity(id, code);
    }

    @Override
    public ConversationIdentity findActiveConversation(Long ownerUserId, Long conversationId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        select id, conversation_code
                        from ai_conversation
                        where owner_user_id = ? and id = ? and is_deleted = 0
                        """,
                ownerUserId,
                conversationId
        );
        return new ConversationIdentity(objectLong(row, "id"), String.valueOf(row.get("conversation_code")));
    }

    @Override
    public void updateLatestMessageAt(Long conversationId, LocalDateTime latestMessageAt, LocalDateTime now) {
        jdbcTemplate.update(
                "update ai_conversation set latest_message_at = ?, update_time = ? where id = ?",
                latestMessageAt,
                now,
                conversationId
        );
    }

    private Long objectLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }
}
