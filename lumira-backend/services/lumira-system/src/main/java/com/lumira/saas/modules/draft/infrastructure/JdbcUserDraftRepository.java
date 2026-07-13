package com.lumira.saas.modules.draft.infrastructure;

import com.lumira.saas.modules.draft.repository.UserDraftRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserDraftRepository implements UserDraftRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserDraftRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserDraft> find(Long userId, String userUuid, String draftKey) {
        List<UserDraft> rows = jdbcTemplate.query("""
                select payload_json, updated_at
                from sys_user_draft
                where user_id = ? and user_uuid = ? and draft_key = ?
                limit 1
                """, (resultSet, rowNum) -> new UserDraft(
                resultSet.getString("payload_json"),
                resultSet.getTimestamp("updated_at").toLocalDateTime()
        ), userId, userUuid, draftKey);
        return rows.stream().findFirst();
    }

    @Override
    public void save(Long userId, String userUuid, String draftKey, String payloadJson) {
        jdbcTemplate.update("""
                insert into sys_user_draft (user_id, user_uuid, draft_key, payload_json)
                values (?, ?, ?, cast(? as json))
                on duplicate key update payload_json = values(payload_json), updated_at = current_timestamp
                """, userId, userUuid, draftKey, payloadJson);
    }

    @Override
    public void delete(Long userId, String userUuid, String draftKey) {
        jdbcTemplate.update("""
                delete from sys_user_draft
                where user_id = ? and user_uuid = ? and draft_key = ?
                """, userId, userUuid, draftKey);
    }
}
