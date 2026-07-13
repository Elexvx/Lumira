package com.lumira.saas.modules.account.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.account.repository.AccountActivationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountActivationRepository implements AccountActivationRepository {
    private final MyBatisQueryOperations database;

    public JdbcAccountActivationRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public Optional<UserIdentity> findUser(Long userId) {
        String uuid = database.queryForObject("select uuid from sys_user where id = ? and deleted = 0 limit 1", String.class, userId);
        if (uuid == null || uuid.isBlank()) return Optional.empty();
        String status = database.queryForObject("select status from sys_user where id = ? and uuid = ? and deleted = 0 limit 1",
                String.class, userId, uuid.trim());
        return Optional.of(new UserIdentity(uuid, status));
    }

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
        List<TokenRecord> rows = database.query("""
                select t.id, t.token_hash as tokenHash, t.user_id as userId, t.user_uuid as userUuid,
                       t.expert_id as expertId, u.username, u.email
                from sys_account_activation_token t
                join sys_user u on u.id = t.user_id and u.uuid = t.user_uuid and u.deleted = 0
                where t.token_hash = ? and t.consumed_at is null and t.expires_at > ? and t.deleted = 0 limit 1
                """, (row, index) -> new TokenRecord(row.getLong("id"), row.getString("tokenHash"), row.getLong("userId"),
                row.getString("userUuid"), row.getObject("expertId", Long.class), row.getString("username"), row.getString("email")), hash, now);
        return rows.stream().findFirst();
    }

    @Override
    public int consumeToken(TokenRecord token, LocalDateTime now) {
        return database.update("""
                update sys_account_activation_token set consumed_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and token_hash = ? and user_id = ? and user_uuid = ? and consumed_at is null and deleted = 0
                """, now, token.userId(), token.userUuid(), now, token.id(), token.tokenHash(), token.userId(), token.userUuid());
    }

    @Override
    public int activateUser(TokenRecord token, String passwordHash, LocalDateTime now) {
        return database.update("""
                update sys_user set password_hash = ?, status = 'ENABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and uuid = ? and deleted = 0 and exists (
                  select 1 from sys_account_activation_token t where t.id = ? and t.token_hash = ?
                    and t.user_id = sys_user.id and t.user_uuid = sys_user.uuid and t.consumed_at = ? and t.deleted = 0)
                """, passwordHash, token.userId(), token.userUuid(), now, token.userId(), token.userUuid(), token.id(), token.tokenHash(), now);
    }

    @Override
    public int activateExpert(TokenRecord token, LocalDateTime now) {
        return database.update("""
                update aiadc_expert set initial_password_reset_required = 0, account_status = 'ENABLED',
                  updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and user_id = ? and user_uuid = ? and deleted = 0
                """, token.userId(), token.userUuid(), now, token.expertId(), token.userId(), token.userUuid());
    }

    @Override
    public Optional<String> findPlatformConfig(String configKey) {
        return Optional.ofNullable(database.queryForObject("""
                select config_value from sys_config where config_key = ? and config_scope = 'PLATFORM' and deleted = 0 limit 1
                """, String.class, configKey));
    }
}
