package com.legendary.invention.saas.modules.ai.app;

import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AiConversationService {

    Long ensureConversation(Long tenantId, Long employeeId, Long conversationId, String title);

    Long recordMessage(Long tenantId, Long conversationId, String role, String content);
}

@Service
@Primary
class JdbcAiConversationService implements AiConversationService {

    private final JdbcTemplate jdbcTemplate;

    JdbcAiConversationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Long ensureConversation(Long tenantId, Long employeeId, Long conversationId, String title) {
        if (conversationId != null) {
            Long foundId = jdbcTemplate.query(
                    """
                            select id
                            from ai_conversation
                            where tenant_id = ?
                              and employee_id = ?
                              and id = ?
                              and is_deleted = 0
                            limit 1
                            """,
                    (rs, rowNum) -> rs.getLong("id"),
                    tenantId,
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
                            tenant_id, employee_id, conversation_code, title, status, latest_message_at, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, 'ACTIVE', null, 0, ?, ?)
                        """,
                tenantId,
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
}
