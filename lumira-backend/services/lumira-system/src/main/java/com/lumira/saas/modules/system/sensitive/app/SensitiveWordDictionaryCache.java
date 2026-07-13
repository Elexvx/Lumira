package com.lumira.saas.modules.system.sensitive.app;

import com.lumira.saas.modules.system.sensitive.repository.SensitiveWordDictionaryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SensitiveWordDictionaryCache {

    private final SensitiveWordDictionaryRepository dictionaryRepository;
    private final SensitiveWordMetrics metrics;

    public SensitiveWordDictionaryCache(
            SensitiveWordDictionaryRepository dictionaryRepository,
            SensitiveWordDictionaryVersionService versionService,
            SensitiveWordMetrics metrics
    ) {
        this.dictionaryRepository = dictionaryRepository;
        this.metrics = metrics;
    }

    public SensitiveWordMatcher getMatcher() {
        metrics.recordCacheHit(false);
        return buildMatcher();
    }

    public void invalidate() {
        // No in-memory business data is retained; subsequent reads query the database directly.
    }

    private SensitiveWordMatcher buildMatcher() {
        Instant startedAt = Instant.now();
        try {
            List<SensitiveWordDictionaryRepository.Entry> rows = dictionaryRepository.findEnabledEntries();
            List<SensitiveWordMatcher.DictionaryEntry> entries = rows.stream()
                    .filter(row -> row.normalizedWord() != null && !row.normalizedWord().isBlank())
                    .map(row -> new SensitiveWordMatcher.DictionaryEntry(
                            row.id(), row.word(), row.normalizedWord(), row.category(), row.severity(),
                            row.action(), row.priority()
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

}
