package com.lumira.api.dictionary;

import java.util.List;

/** Owner-neutral lookup and validation port for configured dictionary values. */
public interface DictionaryValueNormalizer {

    /**
     * Returns enabled values in their configured representation. Domain modules
     * use this only where the stored aggregate state is intentionally
     * case-sensitive or user-visible.
     */
    List<String> enabledValues(String dictionaryCode);

    String normalizeValue(
            String dictionaryCode,
            String value,
            String defaultValue,
            boolean fallbackAllowed,
            String errorMessage
    );
}
