package com.lumira.saas.modules.system.dict.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.system.dict.repository.DictRuntimeRepository;
import com.lumira.saas.common.vo.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DictRuntimeService {

    private final DictRuntimeRepository repository;

    public DictRuntimeService(DictRuntimeRepository repository) {
        this.repository = repository;
    }

    public List<SystemVO.DictItemVO> listEnabledItems(String dictCode) {
        if (!StringUtils.hasText(dictCode)) {
            return List.of();
        }
        return repository.findEnabledItems(dictCode.trim());
    }

    public PageResponse<SystemVO.DictItemVO> searchEnabledItems(
            String dictCode,
            String keyword,
            String parentItemValue,
            boolean rootOnly,
            List<String> values,
            Long pageNo,
            Long pageSize
    ) {
        if (!StringUtils.hasText(dictCode)) {
            PageResponse<SystemVO.DictItemVO> empty = new PageResponse<>();
            empty.setRecords(List.of());
            empty.setTotal(0L);
            empty.setPageNo(1L);
            empty.setPageSize(pageSize == null ? 50L : pageSize);
            return empty;
        }
        List<String> normalizedValues = values == null ? List.of() : values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(100)
                .toList();
        return repository.searchEnabledItems(new DictRuntimeRepository.ItemSearch(
                dictCode.trim(), keyword, parentItemValue, rootOnly, normalizedValues,
                pageNo == null ? 1L : pageNo,
                pageSize == null ? 50L : pageSize
        ));
    }

    public List<String> enabledValues(String dictCode) {
        return listEnabledItems(dictCode).stream()
                .map(SystemVO.DictItemVO::getItemValue)
                .filter(StringUtils::hasText)
                .map(DictRuntimeService::normalizeToken)
                .distinct()
                .toList();
    }

    public String normalizeValue(String dictCode, String value, String defaultValue, boolean fallbackAllowed, String errorMessage) {
        String normalized = normalizeToken(value);
        if (!StringUtils.hasText(normalized)) {
            normalized = normalizeToken(defaultValue);
        }
        if (!StringUtils.hasText(normalized)) {
            if (fallbackAllowed) {
                return normalized;
            }
            throw new BizException(ErrorCode.BAD_REQUEST, firstText(errorMessage, "Invalid dictionary value"));
        }

        List<String> enabledValues = enabledValues(dictCode);
        if (enabledValues.contains(normalized)) {
            return normalized;
        }
        if (fallbackAllowed && enabledValues.isEmpty()) {
            return normalized;
        }
        throw new BizException(ErrorCode.BAD_REQUEST, firstText(errorMessage, "Invalid dictionary value"));
    }

    public String labelOf(String dictCode, String value) {
        String normalized = normalizeToken(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        Map<String, SystemVO.DictItemVO> itemByValue = listEnabledItems(dictCode).stream()
                .filter(item -> StringUtils.hasText(item.getItemValue()))
                .collect(Collectors.toMap(
                        item -> normalizeToken(item.getItemValue()),
                        Function.identity(),
                        (first, ignored) -> first
                ));
        SystemVO.DictItemVO item = itemByValue.get(normalized);
        return item == null ? null : item.getItemLabel();
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }
}
