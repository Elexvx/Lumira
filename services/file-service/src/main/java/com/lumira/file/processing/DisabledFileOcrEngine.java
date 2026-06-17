package com.lumira.file.processing;

import org.springframework.stereotype.Component;

@Component
public class DisabledFileOcrEngine implements FileOcrEngine {

    public static final String ENGINE_NAME = "OCR_DISABLED";

    @Override
    public String engineName() {
        return ENGINE_NAME;
    }

    @Override
    public OcrEngineResult extract(FileOcrRequest request) {
        return new OcrEngineResult(engineName(), FileOcrProcessor.STATUS_SKIPPED, "OCR_DISABLED", "");
    }
}
