package com.lumira.deploy.bootstrap;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.CharBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

final class AdminCredentialBootstrap {

    static final long ADMIN_USER_ID = 1001L;
    static final String PRINCIPAL_KEY = "BUILTIN_ADMIN";
    static final String LEGACY_FIXED_HASH =
            "$2a$" + "10$" + "VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te";

    enum Outcome {
        INITIALIZED,
        ALREADY_INITIALIZED,
        ADOPTED_EXISTING_CREDENTIAL
    }

    Outcome execute(Connection connection, char[] bootstrapPassword) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            AdminRow admin = lockAdmin(connection);
            if (markerExists(connection)) {
                connection.commit();
                return Outcome.ALREADY_INITIALIZED;
            }
            if (hasOperatorManagedCredential(connection, admin)) {
                adoptExistingCredential(connection, admin);
                insertMarker(connection, admin.userUuid(), "EXISTING_CREDENTIAL", false);
                insertAudit(connection, admin.userUuid(), "Adopted existing administrator credential");
                connection.commit();
                return Outcome.ADOPTED_EXISTING_CREDENTIAL;
            }
            if (bootstrapPassword == null || bootstrapPassword.length == 0) {
                throw new BootstrapRequiredException(
                        "Built-in administrator is pending initialization; mount LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE"
                );
            }

            char[] trustedPassword = bootstrapPassword.clone();
            try {
                PasswordRules.validate(trustedPassword);
                String passwordHash = new BCryptPasswordEncoder(12).encode(CharBuffer.wrap(trustedPassword));
                initializeCredential(connection, admin.userUuid(), passwordHash);
                insertMarker(connection, admin.userUuid(), "DOCKER_SECRET", true);
                insertAudit(connection, admin.userUuid(), "Initialized built-in administrator credential");
                connection.commit();
                return Outcome.INITIALIZED;
            } finally {
                java.util.Arrays.fill(trustedPassword, '\0');
            }
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private AdminRow lockAdmin(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select uuid, password_hash, status, deleted from sys_user where id = ? for update")) {
            statement.setLong(1, ADMIN_USER_ID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Built-in administrator row is missing after migrations");
                }
                return new AdminRow(
                        result.getString("uuid"),
                        result.getString("password_hash"),
                        result.getString("status"),
                        result.getInt("deleted")
                );
            }
        }
    }

    private boolean markerExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from platform_bootstrap_credential where principal_key = ?")) {
            statement.setString(1, PRINCIPAL_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean hasOperatorManagedCredential(Connection connection, AdminRow admin) throws SQLException {
        if (admin.deleted() != 0
                || admin.passwordHash() == null
                || admin.passwordHash().isBlank()
                || LEGACY_FIXED_HASH.equals(admin.passwordHash())) {
            return false;
        }
        return true;
    }

    private void adoptExistingCredential(Connection connection, AdminRow admin) throws SQLException {
        boolean enabled = "ENABLED".equalsIgnoreCase(admin.status());
        upsertPasswordCredential(
                connection,
                admin.userUuid(),
                admin.passwordHash(),
                false,
                enabled ? "ENABLED" : "DISABLED"
        );
    }

    private void initializeCredential(Connection connection, String userUuid, String passwordHash) throws SQLException {
        update(connection,
                """
                        update sys_user
                        set password_hash = ?, status = 'ENABLED', deleted = 0,
                            updated_by = 0, updated_at = current_timestamp
                        where id = ? and uuid = ?
                        """,
                passwordHash, ADMIN_USER_ID, userUuid);

        upsertPasswordCredential(connection, userUuid, passwordHash, true, "ENABLED");

        update(connection,
                "update iam_user set status = 'ENABLED', deleted = 0, updated_at = current_timestamp where id = ?",
                ADMIN_USER_ID);
        update(connection,
                """
                        update iam_user_identity
                        set status = 'ENABLED', deleted = 0, updated_at = current_timestamp
                        where user_id = ? and user_uuid = ?
                          and identity_type = 'USERNAME' and identifier_normalized = 'admin'
                        """,
                ADMIN_USER_ID, userUuid);
        update(connection,
                """
                        update iam_subject
                        set status = 'ENABLED', deleted = 0, updated_at = current_timestamp
                        where subject_type = 'USER' and ref_id = ?
                        """,
                ADMIN_USER_ID);
    }

    private void upsertPasswordCredential(
            Connection connection,
            String userUuid,
            String passwordHash,
            boolean passwordChangeRequired,
            String status
    ) throws SQLException {
        Long credentialId = null;
        try (PreparedStatement statement = connection.prepareStatement(
                """
                        select id
                        from iam_user_credential
                        where user_id = ? and user_uuid = ?
                          and credential_type = 'PASSWORD' and version = 1
                        for update
                        """)) {
            statement.setLong(1, ADMIN_USER_ID);
            statement.setString(2, userUuid);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    credentialId = result.getLong(1);
                }
            }
        }
        if (credentialId == null) {
            update(connection,
                    """
                            insert into iam_user_credential (
                                user_id, user_uuid, credential_type, credential_secret, algorithm,
                                version, last_changed_at, password_change_required, status, deleted
                            ) values (?, ?, 'PASSWORD', ?, 'BCRYPT', 1, current_timestamp, ?, ?, 0)
                            """,
                    ADMIN_USER_ID,
                    userUuid,
                    passwordHash,
                    passwordChangeRequired ? 1 : 0,
                    status);
        } else {
            update(connection,
                    """
                            update iam_user_credential
                            set credential_secret = ?, algorithm = 'BCRYPT',
                                last_changed_at = current_timestamp,
                                password_change_required = ?,
                                status = ?, deleted = 0,
                                updated_at = current_timestamp
                            where id = ?
                            """,
                    passwordHash,
                    passwordChangeRequired ? 1 : 0,
                    status,
                    credentialId);
        }
    }

    private void insertMarker(
            Connection connection,
            String userUuid,
            String source,
            boolean passwordChangeRequired
    ) throws SQLException {
        update(connection,
                """
                        insert into platform_bootstrap_credential (
                            principal_key, user_id, user_uuid, initialization_source,
                            password_change_required, initialized_at
                        ) values (?, ?, ?, ?, ?, current_timestamp)
                        """,
                PRINCIPAL_KEY,
                ADMIN_USER_ID,
                userUuid,
                source,
                passwordChangeRequired ? 1 : 0);
    }

    private void insertAudit(Connection connection, String userUuid, String detail) throws SQLException {
        update(connection,
                """
                        insert into audit_operation_log (
                            user_id, user_uuid, username, module_name, action_name,
                            operation_type, result_status, detail_message,
                            created_by, created_by_uuid, deleted
                        ) values (?, ?, 'admin', 'deployment', 'bootstrap-admin-credential',
                                  'INITIALIZE', 'SUCCESS', ?, 0, ?, 0)
                        """,
                ADMIN_USER_ID,
                userUuid,
                detail + " at " + Instant.now(),
                "00000000-0000-0000-0000-000000000000");
    }

    private int update(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement.executeUpdate();
        }
    }

    private record AdminRow(String userUuid, String passwordHash, String status, int deleted) {
    }

    static final class BootstrapRequiredException extends IllegalStateException {
        BootstrapRequiredException(String message) {
            super(message);
        }
    }
}
