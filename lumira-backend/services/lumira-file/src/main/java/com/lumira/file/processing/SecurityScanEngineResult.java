package com.lumira.file.processing;

public record SecurityScanEngineResult(
        String engine,
        String verdict,
        String reason,
        long scannedBytes
) {
}
