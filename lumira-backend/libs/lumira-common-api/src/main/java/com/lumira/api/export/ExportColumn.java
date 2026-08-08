package com.lumira.api.export;

import java.util.function.Function;

/** A single presentation column in an exported spreadsheet. */
public record ExportColumn<T>(
        String key,
        String label,
        boolean defaultSelected,
        Function<T, Object> valueExtractor
) {
}
