package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.dictionary.DictionaryValueNormalizer;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.util.List;
import org.springframework.util.StringUtils;

/** System-owned dictionary adapter exposed through the shared API contract. */
public class SystemDictionaryValueNormalizer implements DictionaryValueNormalizer {

    private final DictRuntimeService dictRuntimeService;

    public SystemDictionaryValueNormalizer(DictRuntimeService dictRuntimeService) {
        this.dictRuntimeService = dictRuntimeService;
    }

    @Override
    public List<String> enabledValues(String dictionaryCode) {
        return dictRuntimeService.listEnabledItems(dictionaryCode).stream()
                .map(SystemVO.DictItemVO::getItemValue)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    @Override
    public String normalizeValue(
            String dictionaryCode,
            String value,
            String defaultValue,
            boolean fallbackAllowed,
            String errorMessage
    ) {
        return dictRuntimeService.normalizeValue(
                dictionaryCode,
                value,
                defaultValue,
                fallbackAllowed,
                errorMessage
        );
    }
}
