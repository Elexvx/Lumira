package com.lumira.file.processing;

import com.lumira.file.config.FileOcrProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TesseractFileOcrEngine implements FileOcrEngine {

    public static final String ENGINE_NAME = "TESSERACT_CLI";

    private final FileOcrProperties properties;

    public TesseractFileOcrEngine(FileOcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public String engineName() {
        return ENGINE_NAME;
    }

    @Override
    public OcrEngineResult extract(FileOcrRequest request) {
        if (!Files.isRegularFile(request.sourcePath())) {
            throw new IllegalStateException("OCR source file is unavailable: " + request.sourcePath());
        }
        Path output = null;
        try {
            output = Files.createTempFile("lumira-ocr-", ".txt");
            ProcessBuilder processBuilder = new ProcessBuilder(
                    properties.getTesseractCommand(),
                    request.sourcePath().toString(),
                    "stdout",
                    "-l",
                    properties.getLanguages()
            );
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(output.toFile());
            Process process = processBuilder.start();
            boolean completed = process.waitFor(properties.getTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException("OCR command timed out after " + Duration.ofMillis(properties.getTimeoutMillis()));
            }
            String outputText = Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException("OCR command failed: " + truncate(outputText));
            }
            String text = normalizeText(outputText);
            if (!StringUtils.hasText(text)) {
                return new OcrEngineResult(engineName(), FileOcrProcessor.STATUS_EMPTY, "NO_TEXT_DETECTED", "");
            }
            return new OcrEngineResult(engineName(), FileOcrProcessor.STATUS_EXTRACTED, "", text);
        } catch (IOException exception) {
            throw new IllegalStateException("OCR command failed to start: " + properties.getTesseractCommand(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OCR command was interrupted", exception);
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                    // Best-effort cleanup; task outcome should be driven by OCR command result.
                }
            }
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value
                .replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\R{3,}", "\n\n")
                .trim();
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }
}
