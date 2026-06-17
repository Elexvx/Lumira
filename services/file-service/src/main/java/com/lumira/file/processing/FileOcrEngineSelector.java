package com.lumira.file.processing;

import com.lumira.file.config.FileOcrProperties;
import org.springframework.stereotype.Component;

@Component
public class FileOcrEngineSelector {

    private final FileOcrProperties properties;
    private final DisabledFileOcrEngine disabledEngine;
    private final TesseractFileOcrEngine tesseractEngine;

    public FileOcrEngineSelector(
            FileOcrProperties properties,
            DisabledFileOcrEngine disabledEngine,
            TesseractFileOcrEngine tesseractEngine
    ) {
        this.properties = properties;
        this.disabledEngine = disabledEngine;
        this.tesseractEngine = tesseractEngine;
    }

    public FileOcrEngine select() {
        return switch (properties.getMode()) {
            case TESSERACT -> tesseractEngine;
            case DISABLED -> disabledEngine;
        };
    }
}
