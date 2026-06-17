package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InlineFileSecurityScanEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void scan_shouldReturnCleanForOrdinaryFile() throws Exception {
        Path source = tempDir.resolve("readme.txt");
        Files.writeString(source, "hello Lumira", StandardCharsets.ISO_8859_1);

        SecurityScanEngineResult result = new InlineFileSecurityScanEngine()
                .scan(new FileSecurityScanRequest(3001L, source, "txt"));

        assertThat(result.engine()).isEqualTo(FileSecurityScanProcessor.ENGINE_NAME);
        assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_CLEAN);
        assertThat(result.scannedBytes()).isEqualTo(12L);
    }

    @Test
    void scan_shouldDetectEicarAcrossBufferBoundary() throws Exception {
        Path source = tempDir.resolve("eicar.txt");
        String content = "a".repeat(8190) + InlineFileSecurityScanEngine.EICAR_SIGNATURE;
        Files.writeString(source, content, StandardCharsets.ISO_8859_1);

        SecurityScanEngineResult result = new InlineFileSecurityScanEngine()
                .scan(new FileSecurityScanRequest(3001L, source, "txt"));

        assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_THREAT_DETECTED);
        assertThat(result.reason()).isEqualTo("EICAR_TEST_SIGNATURE");
    }
}
