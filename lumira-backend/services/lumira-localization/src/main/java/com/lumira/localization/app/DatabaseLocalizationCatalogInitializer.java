package com.lumira.localization.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.localization.repository.LocalizationCatalogRepository;
import com.lumira.localization.repository.LocalizationCatalogRepository.CatalogEntry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "lumira.localization",
        name = "catalog-initialization-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DatabaseLocalizationCatalogInitializer implements ApplicationRunner {

    private static final String CATALOG_RESOURCE = "localization/ui-catalog.json";

    private final LocalizationCatalogRepository catalogRepository;
    private final ObjectMapper objectMapper;

    public DatabaseLocalizationCatalogInitializer(
            LocalizationCatalogRepository catalogRepository,
            ObjectMapper objectMapper
    ) {
        this.catalogRepository = catalogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        Catalog catalog = objectMapper.readValue(new ClassPathResource(CATALOG_RESOURCE).getInputStream(), Catalog.class);
        if (catalog.entries == null || catalog.entries.isEmpty()) {
            return;
        }

        List<CatalogEntry> entries = catalog.entries.stream()
                .map(entry -> new CatalogEntry(
                        entry.messageKey,
                        entry.namespaceCode,
                        entry.sourceLocale,
                        entry.sourceType,
                        entry.sourceRef,
                        entry.translations
                ))
                .toList();
        catalogRepository.initialize(entries);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Catalog {
        public String catalogVersion;
        public List<CatalogResourceEntry> entries = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CatalogResourceEntry {
        public String messageKey;
        public String namespaceCode;
        public String sourceLocale;
        public String sourceType;
        public String sourceRef;
        public Map<String, String> translations = Map.of();
    }
}
