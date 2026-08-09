package com.lumira.saas.modules.competition.export;

import java.util.function.Function;

public record ExportColumn<T>(
        String key,
        String label,
        boolean defaultSelected,
        Function<T, Object> valueExtractor
) {
}
