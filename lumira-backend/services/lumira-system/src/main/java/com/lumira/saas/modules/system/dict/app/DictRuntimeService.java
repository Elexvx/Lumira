package com.lumira.saas.modules.system.dict.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DictRuntimeService {

    private final JdbcTemplate jdbcTemplate;

    public DictRuntimeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SystemVO.DictItemVO> listEnabledItems(String dictCode) {
        if (!StringUtils.hasText(dictCode)) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        select i.id, i.dict_type_id as dictTypeId, i.item_label as itemLabel, i.item_value as itemValue,
                               i.sort_no as sortNo, i.status, i.remark
                        from sys_dict_type t
                        join sys_dict_item i
                          on i.dict_type_id = t.id
                         and i.deleted = 0
                        where t.dict_code = ?
                          and t.deleted = 0
                          and t.status = 'ENABLED'
                          and i.status = 'ENABLED'
                        order by t.is_system desc, t.id desc, i.sort_no asc, i.id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.DictItemVO.class),
                dictCode.trim()
        );
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
