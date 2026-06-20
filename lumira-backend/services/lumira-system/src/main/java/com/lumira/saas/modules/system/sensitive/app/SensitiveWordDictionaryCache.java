package com.lumira.saas.modules.system.sensitive.app;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class SensitiveWordDictionaryCache {

    private final MyBatisQueryOperations jdbcTemplate;
    private final SensitiveWordDictionaryVersionService versionService;
    private final SensitiveWordMetrics metrics;
    private final Cache<CacheKey, SensitiveWordMatcher> matcherCache = CacheBuilder.newBuilder()
            .maximumSize(256)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    public SensitiveWordDictionaryCache(
            MyBatisQueryOperations jdbcTemplate,
            SensitiveWordDictionaryVersionService versionService,
            SensitiveWordMetrics metrics
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.versionService = versionService;
        this.metrics = metrics;
    }

    public SensitiveWordMatcher getMatcher(Long tenantId) {
        CacheKey key = new CacheKey(tenantId, versionService.currentVersion(tenantId));
        SensitiveWordMatcher cached = matcherCache.getIfPresent(key);
        metrics.recordCacheHit(cached != null);
        if (cached != null) {
            return cached;
        }
        try {
            return matcherCache.get(key, () -> buildMatcher(tenantId));
        } catch (ExecutionException exception) {
            metrics.recordBuildFailure();
            throw new IllegalStateException("Failed to build sensitive word dictionary", exception);
        }
    }

    public void invalidate(Long tenantId) {
        versionService.bumpVersion(tenantId);
    }

    private SensitiveWordMatcher buildMatcher(Long tenantId) {
        Instant startedAt = Instant.now();
        try {
            List<DictionaryRow> rows = jdbcTemplate.query(
                    """
                            select id, word, normalized_word as normalizedWord, category, severity
                            from sys_sensitive_word
                            where tenant_id = ?
                              and enabled = 1
                              and deleted = 0
                            """,
                    new BeanPropertyRowMapper<>(DictionaryRow.class),
                    tenantId
            );
            List<SensitiveWordMatcher.DictionaryEntry> entries = rows.stream()
                    .filter(row -> row.getNormalizedWord() != null && !row.getNormalizedWord().isBlank())
                    .map(row -> new SensitiveWordMatcher.DictionaryEntry(
                            row.getId(),
                            row.getWord(),
                            row.getNormalizedWord(),
                            row.getCategory(),
                            row.getSeverity(),
                            severityPriority(row.getSeverity())
                    ))
                    .sorted(Comparator
                            .comparingInt(SensitiveWordMatcher.DictionaryEntry::priority).reversed()
                            .thenComparing(entry -> entry.normalizedWord().length(), Comparator.reverseOrder())
                            .thenComparing(SensitiveWordMatcher.DictionaryEntry::id))
                    .toList();
            return new SensitiveWordMatcher(entries);
        } finally {
            metrics.recordDictionaryBuild(Duration.between(startedAt, Instant.now()));
        }
    }

    private int severityPriority(String severity) {
        if (severity == null) {
            return 10;
        }
        return switch (severity.trim().toUpperCase()) {
            case "CRITICAL" -> 40;
            case "HIGH" -> 30;
            case "MEDIUM" -> 20;
            case "LOW" -> 10;
            default -> 10;
        };
    }

    private record CacheKey(Long tenantId, long version) {
    }

    public static class DictionaryRow {
        private Long id;
        private String word;
        private String normalizedWord;
        private String category;
        private String severity;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getWord() {
            return word;
        }

        public void setWord(String word) {
            this.word = word;
        }

        public String getNormalizedWord() {
            return normalizedWord;
        }

        public void setNormalizedWord(String normalizedWord) {
            this.normalizedWord = normalizedWord;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }
    }
}
