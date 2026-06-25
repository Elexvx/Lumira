package com.lumira.saas.modules.plugin.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class PluginPersistenceMapperHotPathSqlTest {

    @Test
    void hotPathQueriesShouldUseStableOrderAndBoundedListLimits() throws Exception {
        String xml = mapperXml();

        assertStatementContains(xml, "listDefinitions", "where deleted = 0");
        assertStatementContains(xml, "listDefinitions", "order by sort_no asc, plugin_code asc");
        assertStatementContains(xml, "listVersions", "where plugin_code = #{pluginCode}");
        assertStatementContains(xml, "listVersions", "order by created_at desc");
        assertStatementContains(xml, "listRuntimeLogs", "limit 200");
        assertStatementContains(xml, "pluginStatus", "limit 1");
    }

    @Test
    void pluginHotPathMigrationShouldIntroduceHotPathIndexes() throws Exception {
        String migrationSql = migrationSql();

        assertThat(migrationSql).contains("idx_sys_plugin_definition_deleted_status_sort_code");
        assertThat(migrationSql).contains("idx_sys_plugin_version_plugin_deleted_status_created");
        assertThat(migrationSql).contains("idx_sys_plugin_version_plugin_active_deleted");
        assertThat(migrationSql).doesNotContain("sys_plugin_tenant");
        assertThat(migrationSql).contains("idx_sys_plugin_runtime_log_code_deleted_id");
        assertThat(migrationSql).contains("idx_sys_plugin_menu_rel_code_version_deleted_sort");
        assertThat(migrationSql).contains("idx_sys_plugin_permission_rel_code_version_deleted");
    }

    private static String mapperXml() throws Exception {
        try (InputStream input = PluginPersistenceMapperHotPathSqlTest.class
                .getResourceAsStream("/mapper/PluginPersistenceMapper.xml")) {
            assertThat(input).as("mapper XML is available on the test classpath").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String migrationSql() throws IOException {
        return consolidatedSchemaSql();
    }

    private static Path resolvePath(String... candidates) {
        for (String candidate : candidates) {
            Path direct = Path.of(candidate);
            if (Files.exists(direct)) {
                return direct;
            }
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            for (String candidate : candidates) {
                Path path = current.resolve(candidate);
                if (Files.exists(path)) {
                    return path;
                }
            }
            current = current.getParent();
        }
        return Path.of(candidates[0]);
    }

    private static void assertStatementContains(String xml, String statementId, String expectedSql) {
        Pattern pattern = Pattern.compile(
                "<(?:select|insert|update|delete)\\s+id=\"" + Pattern.quote(statementId) + "\"[\\s\\S]*?</(?:select|insert|update|delete)>",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(xml);
        assertThat(matcher.find())
                .as("statement %s exists", statementId)
                .isTrue();
        assertThat(normalizeSql(matcher.group()))
                .as("statement %s SQL", statementId)
                .contains(normalizeSql(expectedSql));
    }

    private static String consolidatedSchemaSql() throws IOException {
        return Files.readString(resolvePath("../../sql/saas.sql", "sql/saas.sql"), StandardCharsets.UTF_8);
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
