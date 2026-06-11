package com.lumira.saas.modules.iam.service;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IamUserServiceSyncTest {

    @Test
    void syncEnabledSysUserKeepsIdentityAndCredentialEnabled() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        service.syncSysUser(buildUser(1001L, "ENABLED", 0), "SYS_USER_SYNC");

        assertIdentitySynced(jdbcTemplate, "alice", "ENABLED", 0);
        assertCredentialSynced(jdbcTemplate, "ENABLED", 0);
    }

    @Test
    void syncDisabledSysUserDoesNotReEnableIdentityOrCredential() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.identityBindings.put("USERNAME:alice", new IdentityRow(11L, 1001L, 0));
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        service.syncSysUser(buildUser(1001L, "DISABLED", 0), "SYS_USER_SYNC");

        assertIdentitySynced(jdbcTemplate, "alice", "DISABLED", 0);
        assertCredentialSynced(jdbcTemplate, "DISABLED", 0);
    }

    @Test
    void syncDeletedSysUserDoesNotRestoreDeletedIamRecords() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.identityBindings.put("USERNAME:alice", new IdentityRow(11L, 1001L, 0));
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        service.syncSysUser(buildUser(1001L, "ENABLED", 1), "SYS_USER_SYNC");

        assertIdentitySynced(jdbcTemplate, "alice", "DISABLED", 1);
        assertCredentialSynced(jdbcTemplate, "DISABLED", 1);
        assertSecuritySettingSynced(jdbcTemplate, 1);
    }

    @Test
    void createUserWithIdentityCanReuseDeletedIdentityBinding() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.identityBindings.put("USERNAME:alice", new IdentityRow(11L, 2002L, 1));
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        assertDoesNotThrow(() -> service.createUserWithIdentity(buildUser(1001L, "ENABLED", 0), "alice", "ADMIN_CREATE"));

        SqlCall call = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("update iam_user_identity"))
                .filter(update -> update.sql.contains("set user_id = ?"))
                .findFirst()
                .orElseThrow();
        assertEquals(1001L, call.args[0]);
        assertEquals("alice", call.args[1]);
        assertEquals(0, call.args[5]);
    }

    private static SysUserEntity buildUser(Long userId, String status, int deleted) {
        SysUserEntity user = new SysUserEntity();
        user.setId(userId);
        user.setUsername("alice");
        user.setMobile("13800138000");
        user.setEmail("Alice@example.com");
        user.setNickname("Alice");
        user.setPasswordHash("{bcrypt}hash");
        user.setStatus(status);
        user.setDeleted(deleted);
        return user;
    }

    private static void assertIdentitySynced(RecordingJdbcTemplate jdbcTemplate, String identifier, String status, int deleted) {
        SqlCall call = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("iam_user_identity"))
                .filter(update -> update.sql.contains("insert into iam_user_identity") || update.sql.contains("update iam_user_identity"))
                .filter(update -> update.sql.contains("insert into iam_user_identity")
                        ? update.args.length > 2 && identifier.equals(update.args[2])
                        : update.args.length > 1 && identifier.equals(update.args[1]))
                .findFirst()
                .orElseThrow();
        if (call.sql.contains("insert into iam_user_identity")) {
            assertEquals(status, call.args[6]);
            assertEquals(deleted, call.args[7]);
            return;
        }
        assertEquals(status, call.args[4]);
        assertEquals(deleted, call.args[5]);
    }

    private static void assertCredentialSynced(RecordingJdbcTemplate jdbcTemplate, String status, int deleted) {
        SqlCall call = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("iam_user_credential"))
                .findFirst()
                .orElseThrow();
        assertEquals(status, call.args[2]);
        assertEquals(deleted, call.args[3]);
    }

    private static void assertSecuritySettingSynced(RecordingJdbcTemplate jdbcTemplate, int deleted) {
        SqlCall call = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("iam_user_security_setting"))
                .findFirst()
                .orElseThrow();
        assertEquals(deleted, call.args[1]);
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<SqlCall> updates = new ArrayList<>();
        private final Map<String, IdentityRow> identityBindings = new HashMap<>();

        @Override
        public int update(String sql, Object... args) {
            updates.add(new SqlCall(sql, args));
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (!sql.contains("from iam_user_identity") || args.length < 2) {
                return List.of();
            }
            String identityType = String.valueOf(args[0]);
            String identifierNormalized = String.valueOf(args[1]);
            IdentityRow row = identityBindings.get(identityType + ":" + identifierNormalized);
            if (row == null) {
                return List.of();
            }
            try {
                return List.of(rowMapper.mapRow(row.toResultSet(), 0));
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private record SqlCall(String sql, Object[] args) {
    }

    private record IdentityRow(Long id, Long userId, int deleted) {
        private ResultSet toResultSet() throws SQLException {
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("id")).thenReturn(id);
            when(resultSet.getLong("user_id")).thenReturn(userId);
            when(resultSet.getInt("deleted")).thenReturn(deleted);
            return resultSet;
        }
    }
}
