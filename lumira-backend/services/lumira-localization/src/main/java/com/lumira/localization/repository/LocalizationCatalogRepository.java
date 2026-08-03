package com.lumira.localization.repository;

import java.util.List;
import java.util.Map;

public interface LocalizationCatalogRepository {

    void initialize(List<CatalogEntry> entries);

    record CatalogEntry(
            String messageKey,
            String namespaceCode,
            String sourceLocale,
            String sourceType,
            String sourceRef,
            Map<String, String> translations
    ) {
    }
}
