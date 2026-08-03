package com.lumira.localization.infrastructure.persistence;

import com.lumira.localization.repository.LocalizationCatalogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcLocalizationCatalogRepository implements LocalizationCatalogRepository {

    private static final String ZH_CN = "zh-CN";
    private static final String EN_US = "en-US";

    private final JdbcTemplate jdbcTemplate;

    public JdbcLocalizationCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void initialize(List<CatalogEntry> entries) {
        seedLanguages();
        seedNamespaces(entries);
        Map<String, Long> namespaceIds = loadNamespaceIds();
        seedEntries(entries, namespaceIds);
        Map<String, Long> entryIds = loadEntryIds();
        seedTranslations(entries, entryIds);
    }

    private void seedLanguages() {
        jdbcTemplate.update(
                """
                INSERT IGNORE INTO sys_localization_language
                    (locale_code, language_name, native_name, fallback_locale, sort_no, is_default, status,
                     created_by, updated_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 'ENABLED', 0, 0, 0)
                """,
                ZH_CN, "Simplified Chinese", "\u7B80\u4F53\u4E2D\u6587", null, 10, 1
        );
        jdbcTemplate.update(
                """
                INSERT IGNORE INTO sys_localization_language
                    (locale_code, language_name, native_name, fallback_locale, sort_no, is_default, status,
                     created_by, updated_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 'ENABLED', 0, 0, 0)
                """,
                EN_US, "English", "English", ZH_CN, 20, 0
        );
    }

    private void seedNamespaces(List<CatalogEntry> entries) {
        Map<String, CatalogEntry> namespaces = new HashMap<>();
        for (CatalogEntry entry : entries) {
            namespaces.putIfAbsent(entry.namespaceCode(), entry);
        }
        List<Object[]> parameters = namespaces.values().stream()
                .map(entry -> new Object[]{
                        entry.namespaceCode(),
                        entry.namespaceCode(),
                        normalizeSourceType(entry.sourceType()),
                        entry.sourceRef()
                })
                .toList();
        jdbcTemplate.batchUpdate(
                """
                INSERT IGNORE INTO sys_localization_namespace
                    (namespace_code, namespace_name, source_type, source_ref, sort_no, status,
                     created_by, updated_by, deleted)
                VALUES (?, ?, ?, ?, 0, 'ENABLED', 0, 0, 0)
                """,
                parameters
        );
    }

    private Map<String, Long> loadNamespaceIds() {
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT id, namespace_code FROM sys_localization_namespace WHERE deleted = 0",
                (row, rowNumber) -> Map.entry(row.getString("namespace_code"), row.getLong("id"))
        ).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private void seedEntries(List<CatalogEntry> entries, Map<String, Long> namespaceIds) {
        List<Object[]> parameters = new ArrayList<>(entries.size());
        for (CatalogEntry entry : entries) {
            Long namespaceId = namespaceIds.get(entry.namespaceCode());
            if (namespaceId == null) {
                continue;
            }
            String defaultMessage = entry.translations() == null
                    ? entry.messageKey()
                    : entry.translations().getOrDefault(entry.sourceLocale(), entry.messageKey());
            parameters.add(new Object[]{
                    namespaceId,
                    entry.messageKey(),
                    defaultMessage,
                    entry.sourceLocale(),
                    normalizeSourceType(entry.sourceType()),
                    entry.sourceRef()
            });
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT IGNORE INTO sys_localization_entry
                    (namespace_id, message_key, default_message, source_locale, source_type, source_ref, status,
                     created_by, updated_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 'ENABLED', 0, 0, 0)
                """,
                parameters
        );
    }

    private Map<String, Long> loadEntryIds() {
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT entry.id, namespace.namespace_code, entry.message_key
                FROM sys_localization_entry entry
                JOIN sys_localization_namespace namespace ON namespace.id = entry.namespace_id AND namespace.deleted = 0
                WHERE entry.deleted = 0
                """,
                (row, rowNumber) -> Map.entry(
                        entryKey(row.getString("namespace_code"), row.getString("message_key")),
                        row.getLong("id")
                )
        ).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private void seedTranslations(List<CatalogEntry> entries, Map<String, Long> entryIds) {
        List<Object[]> parameters = new ArrayList<>(entries.size() * 2);
        for (CatalogEntry entry : entries) {
            Long entryId = entryIds.get(entryKey(entry.namespaceCode(), entry.messageKey()));
            if (entryId == null || entry.translations() == null) {
                continue;
            }
            addTranslation(parameters, entryId, ZH_CN, entry.translations().get(ZH_CN));
            addTranslation(parameters, entryId, EN_US, entry.translations().get(EN_US));
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT IGNORE INTO sys_localization_translation
                    (entry_id, locale_code, translated_message, translation_status, machine_generated,
                     review_status, created_by, updated_by, deleted)
                VALUES (?, ?, ?, 'TRANSLATED', 0, 'APPROVED', 0, 0, 0)
                """,
                parameters
        );
    }

    private void addTranslation(List<Object[]> parameters, Long entryId, String locale, String message) {
        if (message != null && !message.isBlank()) {
            parameters.add(new Object[]{entryId, locale, message});
        }
    }

    private String normalizeSourceType(String sourceType) {
        return "ROUTE".equalsIgnoreCase(sourceType) ? "ROUTE" : "UI";
    }

    private String entryKey(String namespaceCode, String messageKey) {
        return namespaceCode + "\u0000" + messageKey;
    }
}
