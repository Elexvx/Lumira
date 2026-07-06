package com.lumira.saas.modules.iam.service;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        jdbcTemplate.identityBindings.put("USERNAME:alice", new IdentityRow(11L, 1001L, "uuid-1001", 0));
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        service.syncSysUser(buildUser(1001L, "DISABLED", 0), "SYS_USER_SYNC");

        assertIdentitySynced(jdbcTemplate, "alice", "DISABLED", 0);
        assertCredentialSynced(jdbcTemplate, "DISABLED", 0);
    }

    @Test
    void syncDeletedSysUserDoesNotRestoreDeletedIamRecords() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.identityBindings.put("USERNAME:alice", new IdentityRow(11L, 1001L, "uuid-1001", 0));
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        service.syncSysUser(buildUser(1001L, "ENABLED", 1), "SYS_USER_SYNC");

        assertIdentitySynced(jdbcTemplate, "alice", "DISABLED", 1);
        assertCredentialSynced(jdbcTemplate, "DISABLED", 1);
        assertSecuritySettingSynced(jdbcTemplate, 1);
    }

    @Test
    void createUserWithIdentityCanReuseDeletedIdentityBinding() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.identityBindings.put("USERNAME:alice", new IdentityRow(11L, 2002L, "uuid-2002", 1));
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        assertDoesNotThrow(() -> service.createUserWithIdentity(buildUser(1001L, "ENABLED", 0), "alice", "ADMIN_CREATE"));

        SqlCall call = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("update iam_user_identity"))
                .filter(update -> update.sql.contains("set user_id = ?"))
                .findFirst()
                .orElseThrow();
        assertEquals(1001L, call.args[0]);
        assertEquals("uuid-1001", call.args[1]);
        assertEquals("alice", call.args[2]);
        assertEquals(0, call.args[6]);
        assertTrue(call.sql.contains("and deleted = ?"));
        assertTrue(call.sql.contains("and identity_type = ?"));
        assertTrue(call.sql.contains("and identifier_normalized = ?"));
        assertEquals("USERNAME", call.args[8]);
        assertEquals("alice", call.args[9]);
        assertEquals(2002L, call.args[10]);
        assertEquals("uuid-2002", call.args[11]);
        assertEquals(1, call.args[12]);
    }

    @Test
    void bindIdentityShouldRejectWhenInsertMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateCounts.add(0);
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.bindIdentity(1001L, "uuid-1001", IamUserService.IDENTITY_USERNAME, "alice"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("IAM user changed, please retry");
                });

        assertTrue(jdbcTemplate.updates.stream().anyMatch(update -> update.sql.contains("insert into iam_user_identity")));
    }

    @Test
    void syncSysUserShouldRejectWhenIamUserUpsertMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateCounts.add(0);
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.syncSysUser(buildUser(1001L, "ENABLED", 0), "SYS_USER_SYNC"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("IAM user changed, please retry");
                });

        assertEquals(1, jdbcTemplate.updates.size());
    }

    @Test
    void syncSysUserShouldRejectWhenCredentialUpsertMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        addUpdateCounts(jdbcTemplate, 1, 1, 1, 1, 0);
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.syncSysUser(buildUser(1001L, "ENABLED", 0), "SYS_USER_SYNC"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("IAM user changed, please retry");
                });

        assertTrue(jdbcTemplate.updates.stream().anyMatch(update -> update.sql.contains("insert into iam_user_credential")));
    }

    @Test
    void syncSysUserShouldRejectWhenProfileUpsertMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        addUpdateCounts(jdbcTemplate, 1, 1, 1, 1, 1, 0);
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.syncSysUser(buildUser(1001L, "ENABLED", 0), "SYS_USER_SYNC"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("IAM user changed, please retry");
                });

        assertTrue(jdbcTemplate.updates.stream().anyMatch(update -> update.sql.contains("insert into iam_user_profile")));
    }

    @Test
    void syncSysUserShouldRejectWhenSecuritySettingUpsertMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        addUpdateCounts(jdbcTemplate, 1, 1, 1, 1, 1, 1, 0);
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.syncSysUser(buildUser(1001L, "ENABLED", 0), "SYS_USER_SYNC"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("IAM user changed, please retry");
                });

        assertTrue(jdbcTemplate.updates.stream().anyMatch(update -> update.sql.contains("insert into iam_user_security_setting")));
    }

    @Test
    void transferIdentityShouldRejectWhenEventInsertMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.identityBindings.put("USERNAME:alice", new IdentityRow(11L, 1001L, "uuid-1001", 0));
        jdbcTemplate.updateCounts.add(0);
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.transferIdentity(1001L, "uuid-1001", "USERNAME", "alice", 2002L, "operator-uuid-2002", "reason", "127.0.0.1", "JUnit"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("IAM user changed, please retry");
                });

        assertTrue(jdbcTemplate.updates.stream().anyMatch(update -> update.sql.contains("insert into iam_user_event")));
    }

    @Test
    void findByUserIdSelectsUuidForTrustedSnapshotIdentity() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        service.findByUserId(1001L);

        assertTrue(jdbcTemplate.queries.stream()
                .anyMatch(sql -> sql.contains("from sys_user") && sql.contains("select id, uuid, username")));
    }

    @Test
    void transferIdentityShouldRejectOperatorIdWithoutOperatorUuid() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.transferIdentity(1001L, "USERNAME", "alice", 2002L, "reason", "127.0.0.1", "JUnit"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertTrue(jdbcTemplate.updates.isEmpty());
    }

    @Test
    void transferIdentityShouldRecordTrustedOperatorUuid() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        service.transferIdentity(1001L, "uuid-1001", "USERNAME", "alice", 2002L, "operator-uuid-2002", "reason", "127.0.0.1", "JUnit");

        SqlCall event = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("insert into iam_user_event"))
                .findFirst()
                .orElseThrow();
        assertEquals(1001L, event.args[0]);
        assertEquals("uuid-1001", event.args[1]);
        assertEquals(2002L, event.args[4]);
        assertEquals("operator-uuid-2002", event.args[5]);
    }

    @Test
    void changeUserStatusShouldBindIamUserUpdateToTrustedSysUserUuid() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        service.changeUserStatus(1001L, "uuid-1001", "DISABLED");

        SqlCall call = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("update iam_user"))
                .filter(update -> !update.sql.contains("iam_user_identity"))
                .findFirst()
                .orElseThrow();
        assertTrue(call.sql.contains("exists"));
        assertTrue(call.sql.contains("u.uuid = ?"));
        assertEquals("DISABLED", call.args[0]);
        assertEquals(1001L, call.args[1]);
        assertEquals("uuid-1001", call.args[2]);
    }

    @Test
    void changeUserStatusShouldRejectWhenIamUserWriteMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateCounts.add(0);
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.changeUserStatus(1001L, "uuid-1001", "DISABLED"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("IAM user changed, please retry");
                });

        assertEquals(1, jdbcTemplate.updates.size());
    }

    @Test
    void softDeleteUserShouldBindIamUserUpdateToTrustedSysUserUuid() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        service.softDeleteUser(1001L, "uuid-1001");

        SqlCall call = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("update iam_user"))
                .filter(update -> !update.sql.contains("iam_user_identity"))
                .findFirst()
                .orElseThrow();
        assertTrue(call.sql.contains("exists"));
        assertTrue(call.sql.contains("u.uuid = ?"));
        assertEquals(1001L, call.args[0]);
        assertEquals("uuid-1001", call.args[1]);
    }

    @Test
    void softDeleteUserShouldRejectWhenIamUserWriteMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateCounts.add(0);
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.softDeleteUser(1001L, "uuid-1001"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("IAM user changed, please retry");
                });

        assertEquals(1, jdbcTemplate.updates.size());
    }

    @Test
    void unbindIdentityShouldRejectWhenIdentityWriteMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateCounts.add(0);
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.unbindIdentity(1001L, "uuid-1001", IamUserService.IDENTITY_USERNAME, "alice"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("Login identity changed, please retry");
                });
    }

    @Test
    void recordLoginSuccessShouldBindIamUserUpdateToTrustedSysUserUuid() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        service.recordLoginSuccess(1001L, "uuid-1001", "USERNAME", "alice", "127.0.0.1", "JUnit", "device-1");

        SqlCall call = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("update iam_user"))
                .filter(update -> !update.sql.contains("iam_user_identity"))
                .findFirst()
                .orElseThrow();
        assertTrue(call.sql.contains("exists"));
        assertTrue(call.sql.contains("u.uuid = ?"));
        assertEquals(1001L, call.args[2]);
        assertEquals("uuid-1001", call.args[3]);
    }

    @Test
    void recordLoginSuccessShouldRejectWhenIamUserWriteMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateCounts.add(0);
        IamUserService service = new IamUserService(new MyBatisQueryOperations(jdbcTemplate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.recordLoginSuccess(1001L, "uuid-1001", "USERNAME", "alice", "127.0.0.1", "JUnit", "device-1"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("IAM user changed, please retry");
                });

        assertEquals(1, jdbcTemplate.updates.size());
    }

    @Test
    void iamUpsertsShouldNotRewriteTrustedUserIdentityOnDuplicateKeys() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/iam/service/IamUserService.java"));

        assertTrue(source.contains("and identity_type = ?\n                              and identifier_normalized = ?\n                              and ((deleted = 1) or (user_id = ? and user_uuid = ?))"));
        assertTrue(source.contains("where id = ?\n                              and identity_type = ?\n                              and identifier_normalized = ?\n                              and user_id = ?\n                              and user_uuid = ?\n                              and deleted = ?"));
        assertTrue(source.contains("if (updated <= 0)"));
        assertTrue(source.contains("credential_secret = case when user_id = values(user_id) and user_uuid = values(user_uuid)"));
        assertTrue(source.contains("device_name = case when user_id = values(user_id) and user_uuid = values(user_uuid)"));
        assertTrue(source.contains("nickname = case when user_id = values(user_id) and user_uuid = values(user_uuid)"));
        assertTrue(source.contains("on duplicate key update updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid)"));
        assertTrue(!source.contains("user_uuid = values(user_uuid),"));
    }

    private static SysUserEntity buildUser(Long userId, String status, int deleted) {
        SysUserEntity user = new SysUserEntity();
        user.setId(userId);
        user.setUuid("uuid-" + userId);
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
                        ? update.args.length > 3 && identifier.equals(update.args[3])
                        : update.args.length > 2 && identifier.equals(update.args[2]))
                .findFirst()
                .orElseThrow();
        if (call.sql.contains("insert into iam_user_identity")) {
            assertEquals(status, call.args[7]);
            assertEquals(deleted, call.args[8]);
            return;
        }
        assertEquals(status, call.args[5]);
        assertEquals(deleted, call.args[6]);
    }

    private static void assertCredentialSynced(RecordingJdbcTemplate jdbcTemplate, String status, int deleted) {
        SqlCall call = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("iam_user_credential"))
                .findFirst()
                .orElseThrow();
        assertEquals(status, call.args[3]);
        assertEquals(deleted, call.args[4]);
    }

    private static void assertSecuritySettingSynced(RecordingJdbcTemplate jdbcTemplate, int deleted) {
        SqlCall call = jdbcTemplate.updates.stream()
                .filter(update -> update.sql.contains("iam_user_security_setting"))
                .findFirst()
                .orElseThrow();
        assertEquals(deleted, call.args[2]);
    }

    private static void addUpdateCounts(RecordingJdbcTemplate jdbcTemplate, int... counts) {
        for (int count : counts) {
            jdbcTemplate.updateCounts.add(count);
        }
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<SqlCall> updates = new ArrayList<>();
        private final List<String> queries = new ArrayList<>();
        private final Map<String, IdentityRow> identityBindings = new HashMap<>();
        private final List<Integer> updateCounts = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updates.add(new SqlCall(sql, args));
            if (!updateCounts.isEmpty()) {
                return updateCounts.remove(0);
            }
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queries.add(sql);
            if (sql.contains("from sys_user")) {
                return (List<T>) List.of(buildUser(1001L, "ENABLED", 0));
            }
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

    private record IdentityRow(Long id, Long userId, String userUuid, int deleted) {
        private ResultSet toResultSet() throws SQLException {
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("id")).thenReturn(id);
            when(resultSet.getLong("user_id")).thenReturn(userId);
            when(resultSet.getString("user_uuid")).thenReturn(userUuid);
            when(resultSet.getInt("deleted")).thenReturn(deleted);
            return resultSet;
        }
    }
}
