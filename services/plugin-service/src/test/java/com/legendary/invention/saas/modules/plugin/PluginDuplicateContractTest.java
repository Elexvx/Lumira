package com.legendary.invention.saas.modules.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PluginDuplicateContractTest {

    private static final Path BACKEND_PLUGIN_ROOT = Path.of("backend/src/main/java/com/legendary/invention/saas/modules/plugin");
    private static final Path SERVICE_PLUGIN_ROOT = Path.of("services/plugin-service/src/main/java/com/legendary/invention/saas/modules/plugin");

    private static final List<String> SHARED_PLUGIN_FILES = List.of(
            "dto/PluginDTO.java",
            "entity/PluginEntities.java",
            "mapper/PluginRowMappers.java",
            "registry/PluginRuntimeDescriptor.java",
            "runtime/PluginSecurityPropertiesValidator.java",
            "runtime/runtime/PluginRuntimeContext.java",
            "runtime/runtime/PluginRuntimeModels.java",
            "runtime/spi/PluginBootstrap.java",
            "runtime/spi/PluginHealthIndicator.java",
            "runtime/spi/PluginHttpHandler.java",
            "runtime/spi/PluginMenuProvider.java",
            "runtime/spi/PluginPermissionProvider.java",
            "runtime/spi/PluginScheduledTaskProvider.java",
            "runtime/spi/PluginSecondFactorProvider.java",
            "service/PluginPersistenceService.java",
            "vo/PluginVO.java"
    );

    @Test
    void sharedPluginContracts_shouldStayInSyncUntilExtracted() throws IOException {
        Path repoRoot = findRepoRoot();
        for (String relativeFile : SHARED_PLUGIN_FILES) {
            Path backendFile = repoRoot.resolve(BACKEND_PLUGIN_ROOT).resolve(relativeFile);
            Path serviceFile = repoRoot.resolve(SERVICE_PLUGIN_ROOT).resolve(relativeFile);

            assertThat(serviceFile)
                    .as("plugin-service shared file exists: %s", relativeFile)
                    .exists();
            assertThat(backendFile)
                    .as("system-service shared file exists: %s", relativeFile)
                    .exists();
            assertThat(Files.readString(serviceFile, StandardCharsets.UTF_8))
                    .as("shared plugin file should not drift before extraction: %s", relativeFile)
                    .isEqualTo(Files.readString(backendFile, StandardCharsets.UTF_8));
        }
    }

    private static Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("backend"))
                    && Files.isDirectory(current.resolve("services/plugin-service"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate legendary-invention repository root");
    }
}
