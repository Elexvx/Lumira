package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.file.config.FileOcrProperties;
import org.junit.jupiter.api.Test;

class FileOcrEngineSelectorTest {

    @Test
    void select_shouldUseDisabledEngineByDefault() {
        FileOcrProperties properties = new FileOcrProperties();
        FileOcrEngineSelector selector = selector(properties);

        assertThat(selector.select()).isInstanceOf(DisabledFileOcrEngine.class);
    }

    @Test
    void select_shouldUseTesseractWhenConfigured() {
        FileOcrProperties properties = new FileOcrProperties();
        properties.setMode(FileOcrProperties.Mode.TESSERACT);
        FileOcrEngineSelector selector = selector(properties);

        assertThat(selector.select()).isInstanceOf(TesseractFileOcrEngine.class);
    }

    private FileOcrEngineSelector selector(FileOcrProperties properties) {
        return new FileOcrEngineSelector(
                properties,
                new DisabledFileOcrEngine(),
                new TesseractFileOcrEngine(properties)
        );
    }
}
