package com.lumira.saas.modules.system.sensitive.repository;

import java.util.List;

public interface SensitiveWordDictionaryRepository {
    List<Entry> findEnabledEntries();

    record Entry(Long id, String word, String normalizedWord, String category, String severity, String action, int priority) {}
}
