package com.lumira.saas.modules.plugin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PluginArchitectureContractTest {

    @Test
    void legacyBackendDirectory_shouldNotBeAFormalPluginRuntimeSource() {
        Path repoRoot = findRepoRoot();

        assertThat(repoRoot.resolve("lumira-backend"))
                .as("legacy lumira-backend directory must not return as a formal plugin runtime source")
                .doesNotExist();
        assertThat(repoRoot.resolve("services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/app/PluginManagementAppService.java"))
                .as("plugin-service owns plugin management")
                .exists();
    }

    private static Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("services/lumira-plugin"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate lumira repository root");
    }
}
