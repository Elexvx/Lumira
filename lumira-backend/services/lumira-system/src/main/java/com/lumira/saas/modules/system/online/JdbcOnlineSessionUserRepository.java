package com.lumira.saas.modules.system.online;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOnlineSessionUserRepository implements OnlineSessionUserRepository {
    private final MyBatisQueryOperations database;
    public JdbcOnlineSessionUserRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public List<UserRecord> findByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        return database.query("select id, uuid, username, nickname, real_name as realName, avatar_url as avatarUrl from sys_user where deleted = 0 and id in (" + placeholders + ")",
                new BeanPropertyRowMapper<>(UserRecord.class), userIds.toArray());
    }

    @Override
    public Optional<UserRecord> findById(Long userId) {
        return database.query("select id, uuid, username, nickname, real_name as realName, avatar_url as avatarUrl from sys_user where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(UserRecord.class), userId).stream().findFirst();
    }

    @Override
    public int disable(UserRecord user, Long updatedBy, String updatedByUuid, LocalDateTime updatedAt) {
        return database.update("update sys_user set status = 'DISABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and uuid = ? and deleted = 0",
                updatedBy, updatedByUuid, updatedAt, user.getId(), user.getUuid());
    }
}
