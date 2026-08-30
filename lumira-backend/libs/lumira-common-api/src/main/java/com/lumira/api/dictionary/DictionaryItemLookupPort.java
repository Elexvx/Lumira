package com.lumira.api.dictionary;

import java.util.List;

/** Reads enabled System-owned dictionary items for another bounded context. */
public interface DictionaryItemLookupPort {

    List<DictionaryItem> enabledItems(String dictionaryCode);

    default List<DictionaryItem> enabledItemsByValues(String dictionaryCode, List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        java.util.Set<String> requested = new java.util.LinkedHashSet<>(values);
        return enabledItems(dictionaryCode).stream()
                .filter(item -> requested.contains(item.value()))
                .toList();
    }

    record DictionaryItem(
            String label,
            String value,
            String remark,
            int sortNo,
            String parentValue,
            int levelNo,
            boolean leaf
    ) {
        public DictionaryItem(String label, String value, String remark, int sortNo) {
            this(label, value, remark, sortNo, null, 1, true);
        }
    }
}
