package com.lumira.saas.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SystemFileHotPathMigrationTest {

    @Test
    void aggregateServerMigrationShouldProvideFileMapperForcedIndexes() throws Exception {
        String migration = readText(
                "src/main/resources/db/migration/V49__aggregate_file_hot_path_indexes.sql",
                "services/lumira-system/src/main/resources/db/migration/V49__aggregate_file_hot_path_indexes.sql"
        );

        assertThat(migration)
                .contains("idx_file_storage_space_tenant_deleted_default_id")
                .contains("idx_file_object_tenant_deleted_bucket")
                .contains("idx_file_object_tenant_deleted_created_id")
                .contains("idx_file_processing_task_queue")
                .contains("idx_file_processing_task_tenant_created");
    }

    private static String readText(String modulePath, String repoPath) throws Exception {
        Path path = Path.of(modulePath);
        if (!Files.exists(path)) {
            path = Path.of(repoPath);
        }
        assertThat(Files.exists(path)).as("migration exists at %s or %s", modulePath, repoPath).isTrue();
        return Files.readString(path);
    }
}
