package com.lumira.file.repository;

import java.util.List;
import java.util.Optional;

public interface FileBusinessPolicyRepository {
    List<Item> findEnabledItems(String dictionaryCode);

    default Optional<Item> findEnabledItem(String dictionaryCode, String value) {
        if (value == null) return Optional.empty();
        return findEnabledItems(dictionaryCode).stream()
                .filter(item -> value.equalsIgnoreCase(item.value()))
                .findFirst();
    }

    record Item(String label, String value, String remark, int sortNo) {}
}
