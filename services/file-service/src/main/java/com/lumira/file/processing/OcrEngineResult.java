package com.lumira.file.processing;

public record OcrEngineResult(
        String engine,
        String status,
        String reason,
        String text
) {
}
