package com.lumira.saas.modules.plugin.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PluginServiceSqlHotPathTest {

    @Test
    void hotPathSqlInOutboxServiceShouldUseDeleteAwareDispatchFiltersAndBoundedLimit() throws Exception {
        String source = serviceSource("src/main/java/com/lumira/saas/modules/plugin/event/PluginOutboxService.java");

        assertThat(source).contains(normalizeSql("select id, tenant_id as tenantId"));
        assertThat(source).contains(normalizeSql("from plugin_event_outbox"));
        assertThat(source).contains(normalizeSql("where deleted = 0"));
        assertThat(source).contains(normalizeSql("status = ? or (status = ? and (next_retry_at is null or next_retry_at <= ?)"));
        assertThat(source).contains(normalizeSql("order by created_at asc, id asc"));
        assertThat(source).contains(normalizeSql("limit ?"));
        assertThat(source).contains(normalizeSql("sum(case when status = 'PENDING' then 1 else 0 end"));
        assertThat(source).contains(normalizeSql("sum(case when status = 'FAILED' then 1 else 0 end"));
        assertThat(source).contains(normalizeSql("sum(case when status = 'DEAD_LETTER' then 1 else 0 end"));
    }

    @Test
    void pluginOutboxHotPathMigrationShouldIntroduceQueueIndex() throws Exception {
        Path path = resolvePath("src/main/resources/db/migration/plugin/V34__plugin_event_outbox_hot_path.sql");
        if (!Files.exists(path)) {
            path = Path.of("services/lumira-plugin/src/main/resources/db/migration/plugin/V34__plugin_event_outbox_hot_path.sql");
        }
        assertThat(Files.exists(path)).as("plugin outbox hot-path migration exists").isTrue();

        String sql = Files.readString(path, StandardCharsets.UTF_8);
        assertThat(sql).contains("idx_plugin_event_outbox_deleted_status_retry_created");
        assertThat(sql).contains("deleted");
        assertThat(sql).contains("status");
        assertThat(sql).contains("next_retry_at");
        assertThat(sql).contains("created_at");
        assertThat(sql).contains("id");
    }

    private static String serviceSource(String relativePath) throws IOException {
        Path path = resolvePath(relativePath);
        assertThat(Files.exists(path)).as("service source exists").isTrue();
        return normalizeSql(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static Path resolvePath(String relativePath) {
        Path pathFromRepoRoot = Path.of("services/lumira-plugin", relativePath);
        if (Files.exists(pathFromRepoRoot)) {
            return pathFromRepoRoot;
        }
        return Path.of(relativePath);
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
