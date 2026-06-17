package com.lumira.saas.modules.localization.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalizationServiceSqlHotPathTest {

    @Test
    void hotPathSqlShouldUseBatchLanguageAndNamespaceCoverageQueries() throws Exception {
        String source = serviceSource("src/main/resources/mapper/LocalizationManagementMapper.xml");

        assertThat(source).contains(normalizeSql("select l.locale_code as localeCode"));
        assertThat(source).contains(normalizeSql("group by t.locale_code"));
        assertThat(source).contains(normalizeSql("coalesce(t.translated_count, 0) as translatedCount"));
        assertThat(source).contains(normalizeSql("max(release_version) as published_version"));
        assertThat(source).contains(normalizeSql("group by locale_code"));
        assertThat(source).contains(normalizeSql("select n.namespace_code as namespaceCode"));
        assertThat(source).contains(normalizeSql("sum(case when coalesce(t.translated_message, '') &lt;&gt; '' then 1 else 0 end) as translatedCount"));
        assertThat(source).contains(normalizeSql("group by n.namespace_code"));
        assertThat(source).contains(normalizeSql("order by l.is_default desc, l.sort_no asc, l.id asc"));
    }

    @Test
    void localizationHotPathMigrationShouldKeepReaderFriendlyIndexes() throws Exception {
        Path path = resolvePath("src/main/resources/db/migration/localization/V25__localization_hot_path_indexes.sql");
        if (!Files.exists(path)) {
            path = Path.of("services/localization-service/src/main/resources/db/migration/localization/V25__localization_hot_path_indexes.sql");
        }
        assertThat(Files.exists(path)).as("localization hot-path migration exists").isTrue();

        String sql = Files.readString(path, StandardCharsets.UTF_8).toLowerCase();
        assertThat(sql).contains("idx_sys_localization_entry_namespace_deleted_status");
        assertThat(sql).contains("namespace_id");
        assertThat(sql).contains("deleted");
        assertThat(sql).contains("status");
        assertThat(sql).contains("updated_at");
        assertThat(sql).contains("idx_sys_localization_translation_locale_deleted_entry");
        assertThat(sql).contains("locale_code");
        assertThat(sql).contains("entry_id");
        assertThat(sql).contains("idx_sys_localization_namespace_deleted_sort");
        assertThat(sql).contains("sort_no");
        assertThat(sql).contains("id");
    }

    private static String serviceSource(String relativePath) throws IOException {
        Path path = resolvePath(relativePath);
        assertThat(Files.exists(path)).as("service source exists").isTrue();
        return normalizeSql(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static Path resolvePath(String relativePath) {
        Path pathFromRepoRoot = Path.of("services/localization-service", relativePath);
        if (Files.exists(pathFromRepoRoot)) {
            return pathFromRepoRoot;
        }
        return Path.of(relativePath);
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
