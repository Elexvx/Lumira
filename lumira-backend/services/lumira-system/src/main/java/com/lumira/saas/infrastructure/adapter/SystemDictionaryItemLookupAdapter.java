package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.dictionary.DictionaryItemLookupPort;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.util.List;

/** System-owned adapter for dictionary item metadata needed by File policies. */
public class SystemDictionaryItemLookupAdapter implements DictionaryItemLookupPort {

    private final DictRuntimeService dictRuntimeService;

    public SystemDictionaryItemLookupAdapter(DictRuntimeService dictRuntimeService) {
        this.dictRuntimeService = dictRuntimeService;
    }

    @Override
    public List<DictionaryItem> enabledItems(String dictionaryCode) {
        return dictRuntimeService.listEnabledItems(dictionaryCode).stream()
                .map(this::toDictionaryItem)
                .toList();
    }

    @Override
    public List<DictionaryItem> enabledItemsByValues(String dictionaryCode, List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return dictRuntimeService.searchEnabledItems(
                        dictionaryCode, null, null, false, values, 1L, (long) Math.min(100, values.size())
                ).getRecords().stream()
                .map(this::toDictionaryItem)
                .toList();
    }

    private DictionaryItem toDictionaryItem(SystemVO.DictItemVO item) {
        return new DictionaryItem(
                item.getItemLabel(),
                item.getItemValue(),
                item.getRemark(),
                item.getSortNo() == null ? 0 : item.getSortNo(),
                item.getParentItemValue(),
                item.getLevelNo() == null ? 1 : item.getLevelNo(),
                item.getLeaf() == null || item.getLeaf()
        );
    }
}
