package com.lumira.file.processing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.stereotype.Component;

@Component
public class InlineFileSecurityScanEngine implements FileSecurityScanEngine {

    static final String EICAR_SIGNATURE = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE";
    private static final int READ_BUFFER_BYTES = 8192;
    private static final int SIGNATURE_OVERLAP_CHARS = 128;

    @Override
    public String engineName() {
        return FileSecurityScanProcessor.ENGINE_NAME;
    }

    @Override
    public SecurityScanEngineResult scan(FileSecurityScanRequest request) {
        if (!Files.isRegularFile(request.sourcePath())) {
            throw new IllegalStateException("Security scan source file is unavailable: " + request.sourcePath());
        }
        try (InputStream inputStream = Files.newInputStream(request.sourcePath())) {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            String tail = "";
            long scannedBytes = 0L;
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                scannedBytes += read;
                String chunk = tail + new String(buffer, 0, read, StandardCharsets.ISO_8859_1);
                if (chunk.contains(EICAR_SIGNATURE)) {
                    return new SecurityScanEngineResult(engineName(), FileSecurityScanProcessor.VERDICT_THREAT_DETECTED,
                            "EICAR_TEST_SIGNATURE", scannedBytes);
                }
                tail = chunk.length() <= SIGNATURE_OVERLAP_CHARS
                        ? chunk
                        : chunk.substring(chunk.length() - SIGNATURE_OVERLAP_CHARS);
            }
            return new SecurityScanEngineResult(engineName(), FileSecurityScanProcessor.VERDICT_CLEAN, "", scannedBytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Security scan failed: " + request.sourcePath(), exception);
        }
    }
}
