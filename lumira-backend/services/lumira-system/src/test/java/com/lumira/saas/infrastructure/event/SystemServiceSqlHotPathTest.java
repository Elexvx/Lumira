package com.lumira.saas.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SystemServiceSqlHotPathTest {

    @Test
    void hotPathSqlInOutboxServiceShouldUseSystemOwnerBoundedDispatchFilters() throws Exception {
        String source = serviceSource("src/main/java/com/lumira/saas/infrastructure/event/PlatformEventOutboxService.java");

        assertThat(source).contains(normalizeSql("from platform_event_outbox force index (idx_platform_event_outbox_owner_queue)"));
        assertThat(source).contains(normalizeSql("where deleted = 0"));
        assertThat(source).contains(normalizeSql("source_type = ?"));
        assertThat(source).contains(normalizeSql("dispatch_status = ?"));
        assertThat(source).contains(normalizeSql("next_retry_at is null or next_retry_at <= ?"));
        assertThat(source).contains(normalizeSql("order by created_at asc, id asc"));
        assertThat(source).contains(normalizeSql("limit ?"));
        assertThat(source).contains(normalizeSql("where id = ? and deleted = 0 and source_type = ?"));
    }

    @Test
    void systemOutboxMigrationShouldIntroduceOwnerQueueIndex() throws Exception {
        Path path = resolvePath("src/main/resources/db/migration/V19__platform_event_outbox_owner_queue_index.sql");
        if (!Files.exists(path)) {
            path = Path.of("services/lumira-system/src/main/resources/db/migration/V19__platform_event_outbox_owner_queue_index.sql");
        }
        assertThat(Files.exists(path)).as("system outbox owner-queue migration exists").isTrue();

        String sql = Files.readString(path, StandardCharsets.UTF_8).toLowerCase();
        assertThat(sql).contains("idx_platform_event_outbox_owner_queue");
        assertThat(sql).contains("source_type");
        assertThat(sql).contains("created_at");
        assertThat(sql).contains("id");
        assertThat(sql).contains("dispatch_status");
        assertThat(sql).contains("next_retry_at");
        assertThat(sql).contains("deleted");
    }

    private static String serviceSource(String relativePath) throws IOException {
        Path path = resolvePath(relativePath);
        assertThat(Files.exists(path)).as("service source exists").isTrue();
        return normalizeSql(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static Path resolvePath(String relativePath) {
        Path pathFromRepoRoot = Path.of("services/lumira-system", relativePath);
        if (Files.exists(pathFromRepoRoot)) {
            return pathFromRepoRoot;
        }
        return Path.of(relativePath);
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
