package com.lumira.saas.modules.plugin.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.runtime.PluginProperties;
import com.lumira.saas.modules.plugin.service.PluginSemver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginArtifactLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void installToVersionHomeShouldRejectUnsafePluginCode() throws Exception {
        PluginArtifactLoader loader = loader();
        Path extracted = tempDir.resolve("extracted");
        Files.createDirectories(extracted);

        assertThrows(BizException.class, () -> loader.installToVersionHome("../escape", "1.0.0", extracted));
        assertThrows(BizException.class, () -> loader.installToVersionHome("..\\escape", "1.0.0", extracted));
        assertThrows(BizException.class, () -> loader.installToVersionHome("/tmp/escape", "1.0.0", extracted));
    }

    @Test
    void removePathShouldRejectPathsOutsidePluginRoots() throws Exception {
        PluginArtifactLoader loader = loader();
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);

        assertThrows(BizException.class, () -> loader.removePath(outside));
        assertThrows(BizException.class, () -> loader.removePath(tempDir.resolve("plugins")));
        assertThrows(BizException.class, () -> loader.removePath(tempDir.resolve("staging")));
    }

    @Test
    void removePathShouldDeleteOnlyInsidePluginRoots() throws Exception {
        PluginArtifactLoader loader = loader();
        Path versionHome = tempDir.resolve("plugins").resolve("demo").resolve("1.0.0");
        Files.createDirectories(versionHome);
        Files.writeString(versionHome.resolve("plugin.json"), "{}");

        loader.removePath(versionHome);

        assertFalse(Files.exists(versionHome));
    }

    private PluginArtifactLoader loader() {
        PluginProperties properties = new PluginProperties();
        properties.setStorageRoot(tempDir.resolve("plugins").toString());
        properties.setStagingRoot(tempDir.resolve("staging").toString());
        properties.setSignatureSecret("test-secret");
        return new PluginArtifactLoader(new ObjectMapper(), new PluginSemver(), properties);
    }
}
