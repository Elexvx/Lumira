package com.lumira.saas.modules.system.sensitive.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SensitiveWordMatcher {

    private final SensitiveWordAutomaton automaton;

    public SensitiveWordMatcher(List<DictionaryEntry> entries) {
        this.automaton = new SensitiveWordAutomaton(entries);
    }

    public List<Match> find(String text, String fieldPath, int maxMatches) {
        String normalized = normalizeForMatch(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(automaton.find(normalized, fieldPath == null ? "" : fieldPath, maxMatches));
    }

    public static String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
        StringBuilder compacted = new StringBuilder(normalized.length());
        boolean previousWhitespace = false;
        for (int i = 0; i < normalized.length(); i += 1) {
            char ch = normalized.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!previousWhitespace) {
                    compacted.append(' ');
                    previousWhitespace = true;
                }
            } else {
                compacted.append(ch);
                previousWhitespace = false;
            }
        }
        return compacted.toString().trim();
    }

    public record DictionaryEntry(
            Long id,
            String word,
            String normalizedWord,
            String category,
            String severity,
            int priority
    ) {
    }

    public record Match(String fieldPath, String word, String normalizedWord) {
    }
}
