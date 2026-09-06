package com.lumira.deploy.pluginmigration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginMigrationSafetyValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PluginMigrationSafetyValidator validator = new PluginMigrationSafetyValidator(objectMapper);

    @Test
    void acceptsExpandStatementsAndPreservesSemicolonsInsideQuotedDefaults() throws Exception {
        String sql = "CREATE TABLE plugin_sms_message (id bigint NOT NULL, note varchar(64) DEFAULT 'a;b')";
        PluginMigrationRequest request = request(sql, null);

        PluginMigrationRequest.Validated validated = validator.validate(request, "v-release-1");

        assertEquals(1, validated.statements().size());
        assertEquals(sql, validated.statements().getFirst());
        assertEquals(List.of("plugin_sms_message"), validated.targetTables());
    }

    @Test
    void rejectsDestructiveSqlEvenWhenAnAllowedStatementComesFirst() throws Exception {
        String sql = "CREATE TABLE plugin_sms_message (id bigint); DROP TABLE plugin_sms_message";
        PluginMigrationRequest request = request(sql, null);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> validator.validate(request, "v-release-1"));
        assertEquals("migration contains destructive or non-DDL SQL", failure.getMessage());
    }

    @Test
    void rejectsForeignKeysThatEscapeThePluginNamespace() throws Exception {
        String sql = "CREATE TABLE plugin_sms_message (id bigint, user_id bigint, "
                + "CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES sys_user(id))";
        PluginMigrationRequest request = request(sql, null);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> validator.validate(request, "v-release-1"));
        assertEquals("migration references a table outside plugin namespace", failure.getMessage());
    }

    @Test
    void rejectsPayloadWhosePersistedDigestWasChanged() throws Exception {
        PluginMigrationRequest request = request(
                "CREATE TABLE plugin_sms_message (id bigint)", "f".repeat(64));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> validator.validate(request, "v-release-1"));
        assertEquals("migration digest does not match payload", failure.getMessage());
    }

    private PluginMigrationRequest request(String sql, String forcedDigest) throws Exception {
        String pluginCode = "sms";
        String pluginVersion = "1.0.0";
        String packageDigest = "a".repeat(64);
        String schemaVersion = "1";
        String phase = "EXPAND";
        String rollback = "APPLICATION_ONLY";
        String readers = "v-release-1";
        String namespace = "plugin_sms_";
        String step = "V1__sms.sql";
        String payload = objectMapper.writeValueAsString(List.of(Map.of(
                "stepName", step, "scriptPath", "migrations/up/" + step, "sql", sql)));
        String digest = forcedDigest == null
                ? digest(pluginCode, pluginVersion, packageDigest, schemaVersion, phase, rollback, readers, namespace, step, sql)
                : forcedDigest;
        return new PluginMigrationRequest(1L, pluginCode, pluginVersion, schemaVersion, phase, rollback, readers,
                namespace, 1L, packageDigest, digest, "v-release-1", "APPROVED", "MIGRATION_PENDING", payload);
    }

    static String digest(String... values) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String value : values) {
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
