package com.lumira.saas.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SystemFileHotPathMigrationTest {

    @Test
    void consolidatedSchemaShouldProvideFileMapperForcedIndexes() throws Exception {
        String schema = readText("../../sql/saas.sql", "sql/saas.sql");

        assertThat(schema)
                .contains("idx_file_storage_space_deleted_default_id")
                .contains("idx_file_object_deleted_bucket")
                .contains("idx_file_object_deleted_created_id")
                .contains("idx_file_processing_task_queue")
                .contains("idx_file_processing_task_created")
                .doesNotContain("idx_file_storage_space_tenant_deleted_default_id")
                .doesNotContain("idx_file_object_tenant_deleted_bucket")
                .doesNotContain("idx_file_processing_task_tenant_created");
    }

    private static String readText(String... candidates) throws Exception {
        Path path = resolvePath(candidates);
        assertThat(Files.exists(path)).as("consolidated schema exists").isTrue();
        return Files.readString(path);
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
}
