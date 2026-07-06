package com.lumira.saas.modules.workflow.app;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowSchemaBootstrapTrustBoundaryTest {

    @Test
    void bootstrapSeedsShouldUseExplicitSystemActorInsteadOfScatteredZeroAuditValues() throws Exception {
        String source = source("services/lumira-system/src/main/java/com/lumira/saas/modules/workflow/app/WorkflowSchemaBootstrap.java");

        String seedSource = source.substring(source.indexOf("private void seedPermissions()"));
        assertThat(seedSource).contains("SYSTEM_BOOTSTRAP_ACTOR_ID");
        assertThat(seedSource).contains("SYSTEM_BOOTSTRAP_ACTOR_UUID");
        assertThat(seedSource).contains("created_by, created_by_uuid, updated_by, updated_by_uuid");
        assertThat(seedSource).doesNotContain("updated_by = 0");
        assertThat(seedSource).doesNotContain("0, 0, 0");
        assertThat(seedSource).doesNotContain("source_type = values(source_type)");
        assertThat(seedSource).doesNotContain("updated_by_uuid = values(updated_by_uuid),");
        assertThat(seedSource).contains("requireBootstrapWrite(inserted, \"Workflow bootstrap changed, please retry\")");
        assertThat(seedSource).contains("requireBootstrapWriteExact(nodesInserted, 3, \"Workflow bootstrap nodes changed, please retry\")");
        assertThat(seedSource).contains("requireBootstrapWriteExact(edgesInserted, 2, \"Workflow bootstrap edges changed, please retry\")");
        assertThat(seedSource).contains("permission_key = values(permission_key) and source_type = 'CORE' and updated_by_uuid = values(updated_by_uuid)");
        assertThat(seedSource).contains("role_id = values(role_id) and permission_key = values(permission_key) and updated_by_uuid = values(updated_by_uuid)");
        assertThat(seedSource).contains("config_key = values(config_key) and config_scope = values(config_scope) and is_system = values(is_system)");
    }

    private static String source(String relativePath) throws Exception {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct, StandardCharsets.UTF_8);
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Source file not found: " + relativePath);
    }
}
