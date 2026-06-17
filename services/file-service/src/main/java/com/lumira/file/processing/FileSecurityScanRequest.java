package com.lumira.file.processing;

import java.nio.file.Path;

public record FileSecurityScanRequest(
        Long fileId,
        Path sourcePath,
        String extension
) {
}
