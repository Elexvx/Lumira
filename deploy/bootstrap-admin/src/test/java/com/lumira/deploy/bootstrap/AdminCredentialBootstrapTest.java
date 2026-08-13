package com.lumira.deploy.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCredentialBootstrapTest {

    @TempDir
    Path tempDirectory;

    @Test
    void readsUtf8SecretFileAndRemovesOnlyOneTrailingLineEnding() throws Exception {
        Path secretFile = tempDirectory.resolve("admin-password");
        char[] generatedPassword = generatedStrongPassword();
        Files.write(secretFile, (String.valueOf(generatedPassword) + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        char[] secret = BootstrapAdminCommand.readSecret(secretFile.toString());

        assertArrayEquals(generatedPassword, secret);
        java.util.Arrays.fill(secret, '\0');
    }

    @Test
    void initializesPendingAdminAndRequiresFirstPasswordChange() throws Exception {
        try (Connection connection = database()) {
            AdminCredentialBootstrap bootstrap = new AdminCredentialBootstrap();
            char[] generatedPassword = generatedStrongPassword();

            AdminCredentialBootstrap.Outcome outcome =
                    bootstrap.execute(connection, generatedPassword);

            assertEquals(AdminCredentialBootstrap.Outcome.INITIALIZED, outcome);
            assertEquals("ENABLED", value(connection, "select status from sys_user where id = 1001"));
            assertTrue(new BCryptPasswordEncoder().matches(
                    CharBuffer.wrap(generatedPassword),
                    value(connection, "select password_hash from sys_user where id = 1001")
            ));
            assertEquals("1", value(connection,
                    "select password_change_required from iam_user_credential where user_id = 1001"));
            assertEquals("DOCKER_SECRET", value(connection,
                    "select initialization_source from platform_bootstrap_credential where principal_key = 'BUILTIN_ADMIN'"));
            assertEquals("1", value(connection, "select count(*) from audit_operation_log"));
        }
    }

    @Test
    void recordsLocalRandomInitializationSource() throws Exception {
        try (Connection connection = database()) {
            char[] generatedPassword = generatedStrongPassword();
            AdminCredentialBootstrap.Outcome outcome = new AdminCredentialBootstrap().execute(
                    connection,
                    generatedPassword,
                    "LOCAL_RANDOM"
            );

            assertEquals(AdminCredentialBootstrap.Outcome.INITIALIZED, outcome);
            assertEquals("LOCAL_RANDOM", value(connection,
                    "select initialization_source from platform_bootstrap_credential where principal_key = 'BUILTIN_ADMIN'"));
        }
    }

    @Test
    void localDefaultInitializationRequiresLoopbackMysql() {
        assertDoesNotThrow(() -> BootstrapAdminCommand.validateInitializationBoundary(
                "jdbc:mysql://127.0.0.1:3306/lumira?useSSL=false",
                "LOCAL_DEFAULT"
        ));
        assertDoesNotThrow(() -> BootstrapAdminCommand.validateInitializationBoundary(
                "jdbc:mysql://[::1]:3306/lumira",
                "LOCAL_DEFAULT"
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> BootstrapAdminCommand.validateInitializationBoundary(
                        "jdbc:mysql://mysql:3306/saas",
                        "LOCAL_DEFAULT"
                )
        );
        assertDoesNotThrow(() -> BootstrapAdminCommand.validateInitializationBoundary(
                "jdbc:mysql://mysql:3306/saas",
                "DOCKER_SECRET"
        ));
    }

    @Test
    void acceptsDocumentedWeakPasswordOnlyForLocalDefaultInitialization() throws Exception {
        char[] localDefaultPassword = {'1', '2', '3', '4', '5', '6'};
        try (Connection connection = database()) {
            AdminCredentialBootstrap.Outcome outcome = new AdminCredentialBootstrap().execute(
                    connection,
                    localDefaultPassword,
                    "LOCAL_DEFAULT"
            );

            assertEquals(AdminCredentialBootstrap.Outcome.INITIALIZED, outcome);
            assertTrue(new BCryptPasswordEncoder().matches(
                    CharBuffer.wrap(localDefaultPassword),
                    value(connection, "select password_hash from sys_user where id = 1001")
            ));
            assertEquals("1", value(connection,
                    "select password_change_required from iam_user_credential where user_id = 1001"));
            assertEquals("LOCAL_DEFAULT", value(connection,
                    "select initialization_source from platform_bootstrap_credential where principal_key = 'BUILTIN_ADMIN'"));
        }

        try (Connection connection = database()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new AdminCredentialBootstrap().execute(connection, localDefaultPassword)
            );
        }
    }

    @Test
    void rejectsUnknownInitializationSourceBeforeWriting() throws Exception {
        try (Connection connection = database()) {
            char[] generatedPassword = generatedStrongPassword();
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new AdminCredentialBootstrap().execute(
                            connection,
                            generatedPassword,
                            "UNTRUSTED_SOURCE"
                    )
            );

            assertEquals("DISABLED", value(connection, "select status from sys_user where id = 1001"));
            assertEquals("0", value(connection, "select count(*) from platform_bootstrap_credential"));
        }
    }

    @Test
    void isIdempotentAndNeverOverwritesInitializedCredential() throws Exception {
        try (Connection connection = database()) {
            AdminCredentialBootstrap bootstrap = new AdminCredentialBootstrap();
            char[] firstPassword = generatedStrongPassword();
            char[] secondPassword = generatedStrongPassword();
            bootstrap.execute(connection, firstPassword);
            String originalHash = value(connection, "select password_hash from sys_user where id = 1001");

            AdminCredentialBootstrap.Outcome outcome =
                    bootstrap.execute(connection, secondPassword);

            assertEquals(AdminCredentialBootstrap.Outcome.ALREADY_INITIALIZED, outcome);
            assertEquals(originalHash, value(connection, "select password_hash from sys_user where id = 1001"));
            assertEquals("1", value(connection, "select count(*) from platform_bootstrap_credential"));
        }
    }

    @Test
    void failsClosedWhenPendingAdminHasNoMountedSecret() throws Exception {
        try (Connection connection = database()) {
            String legacyFixture = new BCryptPasswordEncoder().encode(CharBuffer.wrap(generatedStrongPassword()));
            execute(connection, "update sys_user set password_hash = '" + legacyFixture + "' where id = 1001");
            AdminCredentialBootstrap bootstrap = new AdminCredentialBootstrap(
                    AdminCredentialBootstrap.sha256Digest(legacyFixture)
            );

            assertThrows(
                    AdminCredentialBootstrap.BootstrapRequiredException.class,
                    () -> bootstrap.execute(connection, null)
            );

            assertEquals("DISABLED", value(connection, "select status from sys_user where id = 1001"));
            assertEquals("0", value(connection, "select count(*) from platform_bootstrap_credential"));
        }
    }

    @Test
    void adoptsAnExistingRotatedCredentialWithoutReplacingIt() throws Exception {
        try (Connection connection = database()) {
            String rotatedHash = new BCryptPasswordEncoder().encode(CharBuffer.wrap(generatedStrongPassword()));
            execute(connection, "update sys_user set password_hash = '" + rotatedHash + "', status = 'ENABLED' where id = 1001");
            execute(connection,
                    "insert into iam_user_credential " +
                            "(user_id,user_uuid,credential_type,credential_secret,algorithm,version,password_change_required,status,deleted) " +
                            "values (1001,'admin-user-1001','PASSWORD','" + rotatedHash + "','BCRYPT',1,0,'ENABLED',0)");

            AdminCredentialBootstrap.Outcome outcome =
                    new AdminCredentialBootstrap().execute(connection, generatedStrongPassword());

            assertEquals(AdminCredentialBootstrap.Outcome.ADOPTED_EXISTING_CREDENTIAL, outcome);
            assertEquals(rotatedHash, value(connection, "select password_hash from sys_user where id = 1001"));
            assertEquals("EXISTING_CREDENTIAL", value(connection,
                    "select initialization_source from platform_bootstrap_credential where principal_key = 'BUILTIN_ADMIN'"));
        }
    }

    @Test
    void repairsIamCredentialFromAnExistingRotatedSystemCredential() throws Exception {
        try (Connection connection = database()) {
            String rotatedHash = new BCryptPasswordEncoder().encode(CharBuffer.wrap(generatedStrongPassword()));
            String staleIamHash = new BCryptPasswordEncoder().encode(CharBuffer.wrap(generatedStrongPassword()));
            execute(connection, "update sys_user set password_hash = '" + rotatedHash + "', status = 'ENABLED' where id = 1001");
            execute(connection,
                    "insert into iam_user_credential " +
                            "(user_id,user_uuid,credential_type,credential_secret,algorithm,version,password_change_required,status,deleted) " +
                            "values (1001,'admin-user-1001','PASSWORD','" +
                            staleIamHash +
                            "','BCRYPT',1,0,'DISABLED',0)");

            AdminCredentialBootstrap.Outcome outcome =
                    new AdminCredentialBootstrap().execute(connection, null);

            assertEquals(AdminCredentialBootstrap.Outcome.ADOPTED_EXISTING_CREDENTIAL, outcome);
            assertEquals(rotatedHash, value(connection,
                    "select credential_secret from iam_user_credential where user_id = 1001"));
            assertEquals("ENABLED", value(connection,
                    "select status from iam_user_credential where user_id = 1001"));
            assertEquals("0", value(connection,
                    "select password_change_required from iam_user_credential where user_id = 1001"));
        }
    }

    @Test
    void rejectsWeakBootstrapPasswordBeforeWritingAnyCredential() throws Exception {
        try (Connection connection = database()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new AdminCredentialBootstrap().execute(connection, generatedWeakPassword())
            );

            assertEquals("", value(connection, "select password_hash from sys_user where id = 1001"));
            assertEquals("0", value(connection, "select count(*) from iam_user_credential"));
        }
    }

    private static char[] generatedStrongPassword() {
        return ("A" + UUID.randomUUID().toString().replace("-", "") + "a1!").toCharArray();
    }

    private static char[] generatedWeakPassword() {
        return UUID.randomUUID().toString().substring(0, 8).toCharArray();
    }

    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        execute(connection, """
                create table sys_user (
                    id bigint primary key,
                    uuid varchar(36) not null,
                    password_hash varchar(255) not null,
                    status varchar(32) not null,
                    updated_by bigint default 0,
                    updated_at timestamp default current_timestamp,
                    deleted tinyint not null default 0
                );
                create table iam_user (
                    id bigint primary key,
                    status varchar(32) not null,
                    updated_at timestamp default current_timestamp,
                    deleted tinyint not null default 0
                );
                create table iam_user_identity (
                    id bigint auto_increment primary key,
                    user_id bigint not null,
                    user_uuid varchar(36) not null,
                    identity_type varchar(32) not null,
                    identifier_normalized varchar(128) not null,
                    status varchar(32) not null,
                    updated_at timestamp default current_timestamp,
                    deleted tinyint not null default 0
                );
                create table iam_user_credential (
                    id bigint auto_increment primary key,
                    user_id bigint not null,
                    user_uuid varchar(36) not null,
                    credential_type varchar(32) not null,
                    credential_secret varchar(512) not null,
                    algorithm varchar(64) not null,
                    version int not null,
                    last_changed_at timestamp default current_timestamp,
                    password_change_required tinyint not null default 0,
                    status varchar(32) not null,
                    updated_at timestamp default current_timestamp,
                    deleted tinyint not null default 0,
                    unique (user_id,user_uuid,credential_type,version)
                );
                create table iam_subject (
                    id bigint auto_increment primary key,
                    subject_type varchar(32) not null,
                    ref_id bigint not null,
                    status varchar(32) not null,
                    updated_at timestamp default current_timestamp,
                    deleted tinyint not null default 0
                );
                create table platform_bootstrap_credential (
                    id bigint auto_increment primary key,
                    principal_key varchar(64) not null unique,
                    user_id bigint not null,
                    user_uuid varchar(36) not null,
                    initialization_source varchar(32) not null,
                    password_change_required tinyint not null default 0,
                    initialized_at timestamp default current_timestamp,
                    created_at timestamp default current_timestamp
                );
                create table audit_operation_log (
                    id bigint auto_increment primary key,
                    user_id bigint,
                    user_uuid varchar(36),
                    username varchar(64),
                    module_name varchar(64),
                    action_name varchar(128),
                    operation_type varchar(32),
                    result_status varchar(32),
                    detail_message varchar(1024),
                    created_by bigint,
                    created_by_uuid varchar(36),
                    deleted tinyint not null default 0
                );
                insert into sys_user (id,uuid,password_hash,status,deleted)
                values (1001,'admin-user-1001','','DISABLED',0);
                insert into iam_user (id,status,deleted) values (1001,'DISABLED',0);
                insert into iam_user_identity
                    (user_id,user_uuid,identity_type,identifier_normalized,status,deleted)
                values (1001,'admin-user-1001','USERNAME','admin','DISABLED',0);
                insert into iam_subject (subject_type,ref_id,status,deleted)
                values ('USER',1001,'DISABLED',0);
                """);
        return connection;
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                if (!command.isBlank()) {
                    statement.execute(command);
                }
            }
        }
    }

    private String value(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
