package com.lumira.saas.modules.system.sensitive.repository;

import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SensitiveWordManagementRepository extends SensitiveWordDictionaryRepository {
    List<String> findEnabledDictValues(String dictCode);
    PageData search(String keyword, Boolean enabled, long offset, long limit);
    Optional<SensitiveWordVO.WordRecord> findById(Long id);
    Long create(WordWrite word, Long userId, String userUuid);
    int update(Long id, String expectedNormalizedWord, WordWrite word, Long userId, String userUuid);
    int enable(Long id, String expectedNormalizedWord, Long userId, String userUuid);
    int delete(Long id, String expectedNormalizedWord, Long userId, String userUuid);
    boolean existsByNormalizedWord(String normalizedWord, Long excludeId);
    Set<String> findExistingNormalizedWords(List<String> normalizedWords);
    int insertImported(List<ImportedWord> words, String category, String severity, String action,
                       Long userId, String userUuid);

    record PageData(List<SensitiveWordVO.WordRecord> records, long total) { }
    record WordWrite(String word, String normalizedWord, String category, String severity, String action, boolean enabled) { }
    record ImportedWord(String normalizedWord, String word) { }
}
