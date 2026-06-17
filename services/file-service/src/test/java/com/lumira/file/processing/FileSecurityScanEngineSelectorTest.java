package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.file.config.FileSecurityScanProperties;
import org.junit.jupiter.api.Test;

class FileSecurityScanEngineSelectorTest {

    @Test
    void select_shouldUseInlineEngineByDefault() {
        FileSecurityScanProperties properties = new FileSecurityScanProperties();
        FileSecurityScanEngineSelector selector = selector(properties);

        assertThat(selector.select()).isInstanceOf(InlineFileSecurityScanEngine.class);
    }

    @Test
    void select_shouldUseClamAvEngineWhenConfigured() {
        FileSecurityScanProperties properties = new FileSecurityScanProperties();
        properties.setMode(FileSecurityScanProperties.Mode.CLAMAV);
        FileSecurityScanEngineSelector selector = selector(properties);

        assertThat(selector.select()).isInstanceOf(ClamAvFileSecurityScanEngine.class);
    }

    private FileSecurityScanEngineSelector selector(FileSecurityScanProperties properties) {
        return new FileSecurityScanEngineSelector(
                properties,
                new InlineFileSecurityScanEngine(),
                new ClamAvFileSecurityScanEngine(properties)
        );
    }
}
