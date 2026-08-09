package com.lumira.api.dictionary;

import java.util.List;

/** Reads enabled System-owned dictionary items for another bounded context. */
public interface DictionaryItemLookupPort {

    List<DictionaryItem> enabledItems(String dictionaryCode);

    record DictionaryItem(String label, String value, String remark, int sortNo) {
    }
}
