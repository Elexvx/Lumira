package com.lumira.file.processing;

import com.lumira.file.config.FileSecurityScanProperties;
import org.springframework.stereotype.Component;

@Component
public class FileSecurityScanEngineSelector {

    private final FileSecurityScanProperties properties;
    private final InlineFileSecurityScanEngine inlineEngine;
    private final ClamAvFileSecurityScanEngine clamAvEngine;

    public FileSecurityScanEngineSelector(
            FileSecurityScanProperties properties,
            InlineFileSecurityScanEngine inlineEngine,
            ClamAvFileSecurityScanEngine clamAvEngine
    ) {
        this.properties = properties;
        this.inlineEngine = inlineEngine;
        this.clamAvEngine = clamAvEngine;
    }

    public FileSecurityScanEngine select() {
        return switch (properties.getMode()) {
            case CLAMAV -> clamAvEngine;
            case INLINE -> inlineEngine;
        };
    }
}
