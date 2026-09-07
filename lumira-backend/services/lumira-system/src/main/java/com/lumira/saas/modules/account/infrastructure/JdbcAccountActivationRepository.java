package com.lumira.saas.modules.account.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.account.repository.AccountActivationRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountActivationRepository implements AccountActivationRepository {
    private final MyBatisQueryOperations database;

    public JdbcAccountActivationRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public void invalidateOpenTokens(Long userId, String userUuid, Long operatorId, String operatorUuid, LocalDateTime now) {
        database.update("""
                update sys_account_activation_token set consumed_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where user_id = ? and user_uuid = ? and consumed_at is null and deleted = 0
                """, now, operatorId, operatorUuid, now, userId, userUuid);
    }

    @Override
    public int insertToken(String hash, Long userId, String userUuid, Long expertId, LocalDateTime expiresAt, Long operatorId, String operatorUuid) {
        return database.update("""
                insert into sys_account_activation_token (token_hash, user_id, user_uuid, expert_id, expires_at,
                  created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, hash, userId, userUuid, expertId, expiresAt, operatorId, operatorUuid, operatorId, operatorUuid);
    }

    @Override
    public Optional<TokenRecord> findValidToken(String hash, LocalDateTime now) {
        java.util.List<TokenRecord> rows = database.query("""
                select id, token_hash as tokenHash, user_id as userId, user_uuid as userUuid,
                       expert_id as expertId
                from sys_account_activation_token
                where token_hash = ? and consumed_at is null and expires_at > ? and deleted = 0 limit 1
                """,
                (row, index) -> new TokenRecord(row.getLong("id"), row.getString("tokenHash"), row.getLong("userId"),
                row.getString("userUuid"), row.getObject("expertId", Long.class)), hash, now);
        return rows.stream().findFirst();
    }

    @Override
    public int consumeToken(TokenRecord token, LocalDateTime now) {
        return database.update("""
                update sys_account_activation_token set consumed_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and token_hash = ? and user_id = ? and user_uuid = ? and consumed_at is null and deleted = 0
                """, now, token.userId(), token.userUuid(), now, token.id(), token.tokenHash(), token.userId(), token.userUuid());
    }

}
