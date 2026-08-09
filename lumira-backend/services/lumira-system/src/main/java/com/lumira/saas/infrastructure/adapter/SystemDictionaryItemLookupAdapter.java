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

    private DictionaryItem toDictionaryItem(SystemVO.DictItemVO item) {
        return new DictionaryItem(
                item.getItemLabel(),
                item.getItemValue(),
                item.getRemark(),
                item.getSortNo() == null ? 0 : item.getSortNo()
        );
    }
}
