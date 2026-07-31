package com.lumira.saas.modules.plugin.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.runtime.PluginProperties;
import com.lumira.saas.modules.plugin.service.PluginSemver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test
    void verifyChecksumsShouldRejectPayloadFileMissingFromManifest() throws Exception {
        PluginArtifactLoader loader = loader();
        Path extracted = tempDir.resolve("extracted-unlisted");
        Files.createDirectories(extracted);
        Files.writeString(extracted.resolve("plugin.json"), "{}");
        Files.writeString(extracted.resolve("unlisted.js"), "alert(1)");

        Map<String, String> checksums = Map.of(
                "plugin.json",
                sha256(Files.readAllBytes(extracted.resolve("plugin.json")))
        );

        assertThrows(BizException.class, () -> loader.verifyChecksums(extracted, checksums));
    }

    @Test
    void verifyChecksumsShouldAcceptExactPayloadCoverage() throws Exception {
        PluginArtifactLoader loader = loader();
        Path extracted = tempDir.resolve("extracted-covered");
        Files.createDirectories(extracted.resolve("lumira-ui"));
        Files.writeString(extracted.resolve("plugin.json"), "{}");
        Files.writeString(extracted.resolve("lumira-ui/main.js"), "export default {}");
        Files.writeString(extracted.resolve("checksums.json"), "{}");
        Files.writeString(extracted.resolve("signature.sig"), "0".repeat(64));

        Map<String, String> checksums = new LinkedHashMap<>();
        checksums.put("plugin.json", sha256(Files.readAllBytes(extracted.resolve("plugin.json"))));
        checksums.put("lumira-ui/main.js", sha256(Files.readAllBytes(extracted.resolve("lumira-ui/main.js"))));

        assertThatCode(() -> loader.verifyChecksums(extracted, checksums)).doesNotThrowAnyException();
    }

    @Test
    void verifySignatureShouldRejectMalformedHexWithoutLeakingComparisonBehavior() {
        PluginArtifactLoader loader = loader();

        assertThrows(BizException.class, () -> loader.verifySignature("{}", "not-hex"));
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private PluginArtifactLoader loader() {
        PluginProperties properties = new PluginProperties();
        properties.setStorageRoot(tempDir.resolve("plugins").toString());
        properties.setStagingRoot(tempDir.resolve("staging").toString());
        properties.setSignatureSecret("test-secret");
        return new PluginArtifactLoader(new ObjectMapper(), new PluginSemver(), properties);
    }
}
