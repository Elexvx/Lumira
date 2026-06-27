package com.lumira.file.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FileServiceSqlHotPathTest {

    @Test
    void hotPathSqlInOutboxServiceShouldUseFileOwnerBoundedDispatchFilters() throws Exception {
        String source = serviceSource("src/main/java/com/lumira/file/event/PlatformEventOutboxService.java");

        assertThat(source).contains(normalizeSql("update platform_event_outbox t join ( select id from platform_event_outbox force index (idx_platform_event_outbox_owner_queue)"));
        assertThat(source).contains(normalizeSql("where deleted = 0"));
        assertThat(source).contains(normalizeSql("source_type = ?"));
        assertThat(source).contains(normalizeSql("dispatch_status = ?"));
        assertThat(source).contains(normalizeSql("next_retry_at is null or next_retry_at <= ?"));
        assertThat(source).contains(normalizeSql("order by created_at asc, id asc"));
        assertThat(source).contains(normalizeSql("limit ?"));
        assertThat(source).contains(normalizeSql("where deleted = 0 and source_type = ? and claim_token = ?"));
        assertThat(source).contains(normalizeSql("where id = ? and deleted = 0 and source_type = ?"));
    }

    @Test
    void fileOutboxMigrationShouldIntroduceOwnerQueueIndex() throws Exception {
        String sql = consolidatedSchemaSql().toLowerCase();
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
        Path pathFromRepoRoot = Path.of("services/lumira-file", relativePath);
        if (Files.exists(pathFromRepoRoot)) {
            return pathFromRepoRoot;
        }
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path repoPath = current.resolve("services/lumira-file").resolve(relativePath);
            if (Files.exists(repoPath)) {
                return repoPath;
            }
            Path currentPath = current.resolve(relativePath);
            if (Files.exists(currentPath)) {
                return currentPath;
            }
            current = current.getParent();
        }
        return direct;
    }

    private static Path resolvePath(String firstCandidate, String secondCandidate) {
        Path first = resolvePath(firstCandidate);
        if (Files.exists(first)) {
            return first;
        }
        return resolvePath(secondCandidate);
    }

    private static String consolidatedSchemaSql() throws IOException {
        return Files.readString(resolvePath("../../sql/saas.sql", "sql/saas.sql"), StandardCharsets.UTF_8);
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
