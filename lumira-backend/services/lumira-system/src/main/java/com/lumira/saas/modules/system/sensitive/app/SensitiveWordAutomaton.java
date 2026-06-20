package com.lumira.saas.modules.system.sensitive.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

final class SensitiveWordAutomaton {

    private final Node root = new Node();

    SensitiveWordAutomaton(List<SensitiveWordMatcher.DictionaryEntry> entries) {
        if (entries != null) {
            for (SensitiveWordMatcher.DictionaryEntry entry : entries) {
                add(entry);
            }
        }
        buildFailureLinks();
    }

    List<SensitiveWordMatcher.Match> find(String normalizedText, String fieldPath, int maxMatches) {
        if (normalizedText == null || normalizedText.isBlank() || maxMatches <= 0) {
            return List.of();
        }
        List<SensitiveWordMatcher.Match> matches = new ArrayList<>(Math.min(maxMatches, 16));
        Map<String, SensitiveWordMatcher.Match> deduplicated = new LinkedHashMap<>();
        Node current = root;
        for (int index = 0; index < normalizedText.length(); index += 1) {
            char ch = normalizedText.charAt(index);
            while (current != root && !current.next.containsKey(ch)) {
                current = current.fail;
            }
            current = current.next.getOrDefault(ch, root);
            if (current.outputs.isEmpty()) {
                continue;
            }
            for (SensitiveWordMatcher.DictionaryEntry entry : current.outputs) {
                String key = fieldPath + "\u0000" + entry.normalizedWord();
                deduplicated.putIfAbsent(key, new SensitiveWordMatcher.Match(fieldPath, entry.word(), entry.normalizedWord()));
                if (deduplicated.size() >= maxMatches) {
                    matches.addAll(deduplicated.values());
                    return matches;
                }
            }
        }
        matches.addAll(deduplicated.values());
        return matches;
    }

    private void add(SensitiveWordMatcher.DictionaryEntry entry) {
        if (entry == null || entry.normalizedWord() == null || entry.normalizedWord().isBlank()) {
            return;
        }
        Node node = root;
        for (int i = 0; i < entry.normalizedWord().length(); i += 1) {
            char ch = entry.normalizedWord().charAt(i);
            node = node.next.computeIfAbsent(ch, ignored -> new Node());
        }
        node.outputs.add(entry);
        node.outputs.sort(Comparator
                .comparingInt(SensitiveWordMatcher.DictionaryEntry::priority).reversed()
                .thenComparing(SensitiveWordMatcher.DictionaryEntry::id));
    }

    private void buildFailureLinks() {
        Queue<Node> queue = new ArrayDeque<>();
        for (Node child : root.next.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node current = queue.remove();
            for (Map.Entry<Character, Node> transition : current.next.entrySet()) {
                char ch = transition.getKey();
                Node target = transition.getValue();
                Node fallback = current.fail;
                while (fallback != root && !fallback.next.containsKey(ch)) {
                    fallback = fallback.fail;
                }
                target.fail = fallback.next.getOrDefault(ch, root);
                target.outputs.addAll(target.fail.outputs);
                target.outputs.sort(Comparator
                        .comparingInt(SensitiveWordMatcher.DictionaryEntry::priority).reversed()
                        .thenComparing(SensitiveWordMatcher.DictionaryEntry::id));
                queue.add(target);
            }
        }
    }

    private static final class Node {
        private final Map<Character, Node> next = new HashMap<>();
        private final List<SensitiveWordMatcher.DictionaryEntry> outputs = new ArrayList<>();
        private Node fail;
    }
}
