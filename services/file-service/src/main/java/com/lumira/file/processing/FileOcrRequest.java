package com.lumira.file.processing;

import java.nio.file.Path;

public record FileOcrRequest(
        Long fileId,
        Path sourcePath,
        String contentType,
        String extension
) {
}
