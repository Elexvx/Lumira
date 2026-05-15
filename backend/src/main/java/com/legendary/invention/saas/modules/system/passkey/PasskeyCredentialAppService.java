package com.legendary.invention.saas.modules.system.passkey;

import com.legendary.invention.api.system.PasskeyCredentialDTO;
import com.legendary.invention.api.system.PasskeyCredentialSaveRequestDTO;
import com.legendary.invention.api.system.PasskeyCredentialUsageRequestDTO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PasskeyCredentialAppService {
    private final JdbcTemplate jdbcTemplate;

    public PasskeyCredentialAppService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PasskeyCredentialDTO findByCredentialId(String credentialId) {
        if (!StringUtils.hasText(credentialId)) {
            return null;
        }
        List<PasskeyCredentialDTO> rows = jdbcTemplate.query("""
                select c.*, u.username
                from sys_user_passkey_credential c
                join sys_user u on u.id = c.user_id and u.deleted = 0
                where c.credential_id = ? and c.deleted = 0
                limit 1
                """, mapper(), credentialId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<PasskeyCredentialDTO> list(Long tenantId, Long userId) {
        return jdbcTemplate.query("""
                select c.*, u.username
                from sys_user_passkey_credential c
                join sys_user u on u.id = c.user_id and u.deleted = 0
                where c.tenant_id = ? and c.user_id = ? and c.deleted = 0
                order by c.last_used_at desc, c.created_at desc, c.id desc
                """, mapper(), tenantId, userId);
    }

    public PasskeyCredentialDTO create(PasskeyCredentialSaveRequestDTO request) {
        try {
            jdbcTemplate.update("""
                    insert into sys_user_passkey_credential (
                        tenant_id, user_id, user_handle, credential_id, public_key_cose, sign_count,
                        transports, backup_eligible, backup_state, label, deleted, created_by, updated_by
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """,
                    request.tenantId(),
                    request.userId(),
                    request.userHandle(),
                    request.credentialId(),
                    request.publicKeyCose(),
                    request.signCount() == null ? 0L : request.signCount(),
                    request.transports(),
                    Boolean.TRUE.equals(request.backupEligible()),
                    Boolean.TRUE.equals(request.backupState()),
                    StringUtils.hasText(request.label()) ? request.label().trim() : "通行密钥",
                    request.userId(),
                    request.userId());
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("该通行密钥已绑定");
        }
        return findByCredentialId(request.credentialId());
    }

    public PasskeyCredentialDTO rename(Long id, Long tenantId, Long userId, String label) {
        jdbcTemplate.update("""
                update sys_user_passkey_credential
                set label = ?, updated_by = ?, updated_at = ?
                where id = ? and tenant_id = ? and user_id = ? and deleted = 0
                """, StringUtils.hasText(label) ? label.trim() : "通行密钥", userId, LocalDateTime.now(), id, tenantId, userId);
        return list(tenantId, userId).stream().filter(item -> id.equals(item.id())).findFirst().orElse(null);
    }

    public boolean delete(Long id, Long tenantId, Long userId) {
        return jdbcTemplate.update("""
                update sys_user_passkey_credential
                set deleted = 1, updated_by = ?, updated_at = ?
                where id = ? and tenant_id = ? and user_id = ? and deleted = 0
                """, userId, LocalDateTime.now(), id, tenantId, userId) > 0;
    }

    public boolean updateUsage(PasskeyCredentialUsageRequestDTO request) {
        return jdbcTemplate.update("""
                update sys_user_passkey_credential
                set sign_count = greatest(sign_count, ?),
                    backup_eligible = ?,
                    backup_state = ?,
                    last_used_at = ?,
                    updated_at = ?
                where id = ? and deleted = 0
                """,
                request.signCount() == null ? 0L : request.signCount(),
                Boolean.TRUE.equals(request.backupEligible()),
                Boolean.TRUE.equals(request.backupState()),
                LocalDateTime.now(),
                LocalDateTime.now(),
                request.credentialId()) > 0;
    }

    private RowMapper<PasskeyCredentialDTO> mapper() {
        return (rs, rowNum) -> new PasskeyCredentialDTO(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("user_handle"),
                rs.getString("credential_id"),
                rs.getString("public_key_cose"),
                rs.getLong("sign_count"),
                rs.getString("transports"),
                rs.getBoolean("backup_eligible"),
                rs.getBoolean("backup_state"),
                rs.getString("label"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("last_used_at"))
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
