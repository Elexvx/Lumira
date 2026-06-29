package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiMessageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class JdbcAiMessageRepository extends JdbcAiRepositorySupport implements AiMessageRepository {
    public JdbcAiMessageRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public void addMessage(Long conversationId, String role, String content, LocalDateTime now) {
        jdbcTemplate.update(
                """
                        insert into ai_message (
                            conversation_id, role, content, is_deleted, create_time, update_time
                        ) values (?, ?, ?, 0, ?, ?)
                        """,
                conversationId,
                role,
                content,
                now,
                now
        );
    }
}
