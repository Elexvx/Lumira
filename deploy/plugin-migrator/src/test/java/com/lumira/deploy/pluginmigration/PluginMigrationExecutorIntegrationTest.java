package com.lumira.deploy.pluginmigration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PluginMigrationExecutorIntegrationTest {
    private static final String RELEASE_ID = "v-integration-1";
    private static final String PACKAGE_DIGEST = "a".repeat(64);

    @Container
    private final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("lumira")
            .withUsername("lumira_migrator")
            .withPassword("migrator-test-password");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PluginMigrationRepository repository = new PluginMigrationRepository();
    private final PluginMigrationExecutor executor = new PluginMigrationExecutor(
            repository, new PluginMigrationSafetyValidator(objectMapper));

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS plugin_sms_message");
            statement.execute("DROP TABLE IF EXISTS plugin_sms_bad");
            statement.execute("DROP TABLE IF EXISTS sys_plugin_migration_audit");
            statement.execute("DROP TABLE IF EXISTS sys_plugin_migration_request");
            statement.execute("DROP TABLE IF EXISTS sys_plugin_version");
            statement.execute("""
                    CREATE TABLE sys_plugin_version (
                      id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      plugin_code varchar(64) NOT NULL,
                      version varchar(32) NOT NULL,
                      lifecycle_status varchar(32) NOT NULL,
                      schema_status varchar(32) NOT NULL,
                      deleted tinyint NOT NULL DEFAULT 0,
                      updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE sys_plugin_migration_request (
                      id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      plugin_code varchar(64) NOT NULL, plugin_version varchar(32) NOT NULL,
                      schema_version varchar(64) NOT NULL, phase varchar(16) NOT NULL,
                      rollback_mode varchar(32) NOT NULL, compatible_readers varchar(1024) NOT NULL,
                      table_namespace varchar(128) NOT NULL, operation_epoch bigint NOT NULL,
                      package_digest char(64) NOT NULL, migration_digest char(64) NOT NULL,
                      release_id varchar(128) NOT NULL, request_status varchar(32) NOT NULL,
                      lifecycle_status varchar(32) NOT NULL, script_payload longtext NOT NULL,
                      failure_reason varchar(1024), recovery_action varchar(1024),
                      approved_by varchar(128), approval_reason varchar(512),
                      approved_at datetime, started_at datetime, finished_at datetime,
                      updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE sys_plugin_migration_audit (
                      id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, request_id bigint NOT NULL,
                      plugin_code varchar(64) NOT NULL, plugin_version varchar(32) NOT NULL,
                      event_type varchar(32) NOT NULL, operation_epoch bigint NOT NULL,
                      package_digest char(64) NOT NULL, migration_digest char(64) NOT NULL,
                      release_id varchar(128) NOT NULL, actor varchar(128) NOT NULL,
                      detail_message varchar(1024), created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    @Test
    void approvedExpandRequestIsClaimedExecutedCompletedAndAudited() throws Exception {
        long requestId;
        try (Connection connection = connection()) {
            requestId = insert(connection, "PENDING_APPROVAL",
                    "CREATE TABLE plugin_sms_message (id bigint NOT NULL PRIMARY KEY)", null);
            PluginMigrationRequest pending = repository.find(connection, requestId);
            executor.approve(connection, new PluginMigrationExecutor.Approval(
                    requestId, 1L, PACKAGE_DIGEST, pending.migrationDigest(), RELEASE_ID,
                    "operator@example.com", "reviewed expand-only SQL"));

            PluginMigrationExecutor.ExecutionSummary summary =
                    executor.executeApproved(connection, RELEASE_ID, "integration-migrator", 25);

            assertEquals(new PluginMigrationExecutor.ExecutionSummary(1, 1, 1), summary);
            assertEquals("SUCCEEDED", scalar(connection,
                    "select request_status from sys_plugin_migration_request where id = " + requestId));
            assertEquals("MIGRATED", scalar(connection,
                    "select lifecycle_status from sys_plugin_version where plugin_code = 'sms'"));
            assertTrue(tableExists(connection, "plugin_sms_message"));
            assertEquals(3L, count(connection, "select count(*) from sys_plugin_migration_audit where request_id = " + requestId));
            assertEquals("operator@example.com", scalar(connection,
                    "select approved_by from sys_plugin_migration_request where id = " + requestId));
        }
    }

    @Test
    void secondValidationRejectsDestructiveApprovedPayloadAndPersistsFailureWithoutDdl() throws Exception {
        try (Connection connection = connection()) {
            long requestId = insert(connection, "APPROVED", "DROP TABLE plugin_sms_bad", null);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> executor.executeApproved(connection, RELEASE_ID, "integration-migrator", 25));

            assertTrue(failure.getMessage().contains("failed"));
            assertEquals("FAILED", scalar(connection,
                    "select request_status from sys_plugin_migration_request where id = " + requestId));
            assertEquals("FAILED", scalar(connection,
                    "select lifecycle_status from sys_plugin_migration_request where id = " + requestId));
            assertFalse(tableExists(connection, "plugin_sms_bad"));
            assertEquals(2L, count(connection, "select count(*) from sys_plugin_migration_audit where request_id = " + requestId));
        }
    }

    @Test
    void payloadDigestTamperingFailsClosedBeforeDdlAndDoesNotMarkMigrated() throws Exception {
        try (Connection connection = connection()) {
            long requestId = insert(connection, "APPROVED",
                    "CREATE TABLE plugin_sms_message (id bigint)", "f".repeat(64));

            assertThrows(IllegalStateException.class,
                    () -> executor.executeApproved(connection, RELEASE_ID, "integration-migrator", 25));

            assertEquals("FAILED", scalar(connection,
                    "select request_status from sys_plugin_migration_request where id = " + requestId));
            assertEquals("FAILED", scalar(connection,
                    "select lifecycle_status from sys_plugin_version where plugin_code = 'sms'"));
            assertFalse(tableExists(connection, "plugin_sms_message"));
        }
    }

    private long insert(Connection connection, String status, String sql, String forcedDigest) throws Exception {
        String step = "V1__sms.sql";
        String payload = objectMapper.writeValueAsString(List.of(Map.of(
                "stepName", step, "scriptPath", "migrations/up/" + step, "sql", sql)));
        String digest = forcedDigest == null
                ? PluginMigrationSafetyValidatorTest.digest(
                "sms", "1.0.0", PACKAGE_DIGEST, "1", "EXPAND", "APPLICATION_ONLY",
                RELEASE_ID, "plugin_sms_", step, sql)
                : forcedDigest;
        try (var version = connection.prepareStatement("""
                insert into sys_plugin_version (plugin_code, version, lifecycle_status, schema_status)
                values ('sms', '1.0.0', 'MIGRATION_PENDING', 'PENDING')
                """)) {
            version.executeUpdate();
        }
        try (var request = connection.prepareStatement("""
                insert into sys_plugin_migration_request (
                  plugin_code, plugin_version, schema_version, phase, rollback_mode, compatible_readers,
                  table_namespace, operation_epoch, package_digest, migration_digest, release_id,
                  request_status, lifecycle_status, script_payload, approved_by, approval_reason
                ) values ('sms', '1.0.0', '1', 'EXPAND', 'APPLICATION_ONLY', ?, 'plugin_sms_', 1, ?, ?, ?,
                          ?, 'MIGRATION_PENDING', ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            request.setString(1, RELEASE_ID);
            request.setString(2, PACKAGE_DIGEST);
            request.setString(3, digest);
            request.setString(4, RELEASE_ID);
            request.setString(5, status);
            request.setString(6, payload);
            request.setString(7, "APPROVED".equals(status) ? "test-operator" : null);
            request.setString(8, "APPROVED".equals(status) ? "pre-approved for adversarial test" : null);
            request.executeUpdate();
            try (ResultSet keys = request.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("request id was not generated");
                return keys.getLong(1);
            }
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (var statement = connection.prepareStatement("""
                select count(*) from information_schema.tables where table_schema = database() and table_name = ?
                """)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1) == 1;
            }
        }
    }

    private String scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
