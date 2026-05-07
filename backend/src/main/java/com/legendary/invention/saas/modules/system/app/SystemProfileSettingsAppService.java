package com.legendary.invention.saas.modules.system.app;

import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SystemProfileSettingsAppService {

    private static final Long DEFAULT_PUBLIC_TENANT_ID = 1001L;

    private static final String PROFILE_FIELD_AVATAR_VISIBLE_KEY = "profile.field.avatar.visible";
    private static final String PROFILE_FIELD_REAL_NAME_VISIBLE_KEY = "profile.field.real-name.visible";
    private static final String PROFILE_FIELD_MOBILE_VISIBLE_KEY = "profile.field.mobile.visible";
    private static final String PROFILE_FIELD_EMAIL_VISIBLE_KEY = "profile.field.email.visible";
    private static final String PROFILE_FIELD_BIRTH_MONTH_VISIBLE_KEY = "profile.field.birth-month.visible";
    private static final String PROFILE_FIELD_GENDER_VISIBLE_KEY = "profile.field.gender.visible";
    private static final String PROFILE_FIELD_REGION_VISIBLE_KEY = "profile.field.region.visible";
    private static final String PROFILE_FIELD_AVAILABLE_TIME_VISIBLE_KEY = "profile.field.available-time.visible";
    private static final String PROFILE_FIELD_ID_CARD_VISIBLE_KEY = "profile.field.id-card-number.visible";

    private static final List<ProfileFieldDefinition> PROFILE_FIELD_DEFINITIONS = List.of(
            new ProfileFieldDefinition("avatarUrl", "头像", "控制个人中心是否展示头像上传与预览区域", PROFILE_FIELD_AVATAR_VISIBLE_KEY, true),
            new ProfileFieldDefinition("realName", "姓名", "控制个人中心是否展示姓名字段", PROFILE_FIELD_REAL_NAME_VISIBLE_KEY, true),
            new ProfileFieldDefinition("mobile", "手机号", "控制个人中心是否展示手机号字段", PROFILE_FIELD_MOBILE_VISIBLE_KEY, true),
            new ProfileFieldDefinition("email", "邮箱", "控制个人中心是否展示邮箱字段", PROFILE_FIELD_EMAIL_VISIBLE_KEY, true),
            new ProfileFieldDefinition("birthMonth", "出生年月", "控制个人中心是否展示出生年月字段", PROFILE_FIELD_BIRTH_MONTH_VISIBLE_KEY, true),
            new ProfileFieldDefinition("gender", "性别", "控制个人中心是否展示性别字段", PROFILE_FIELD_GENDER_VISIBLE_KEY, true),
            new ProfileFieldDefinition("region", "所在地区", "控制个人中心是否展示所在地区字段", PROFILE_FIELD_REGION_VISIBLE_KEY, true),
            new ProfileFieldDefinition("availableTime", "可工作时间", "控制个人中心是否展示可工作时间字段", PROFILE_FIELD_AVAILABLE_TIME_VISIBLE_KEY, true),
            new ProfileFieldDefinition("idCardNumber", "身份证号码", "控制个人中心是否展示身份证号码字段", PROFILE_FIELD_ID_CARD_VISIBLE_KEY, true)
    );
    private static final List<String> PROFILE_FIELD_CONFIG_KEYS = PROFILE_FIELD_DEFINITIONS.stream()
            .map(ProfileFieldDefinition::configKey)
            .toList();

    private final JdbcTemplate jdbcTemplate;
    private final OperationAuditService operationAuditService;

    public SystemProfileSettingsAppService(JdbcTemplate jdbcTemplate, OperationAuditService operationAuditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationAuditService = operationAuditService;
    }

    public List<SystemVO.ProfileFieldSettingVO> getProfileFieldSettings(CurrentUser currentUser) {
        return loadProfileFieldSettings(currentTenantId(currentUser));
    }

    @Transactional
    public List<SystemVO.ProfileFieldSettingVO> updateProfileFieldSettings(CurrentUser currentUser, SystemDTO.ProfileFieldSettingsRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Map<String, Boolean> requestedVisibility = new LinkedHashMap<>();
        request.getItems().forEach(item -> requestedVisibility.put(item.getFieldKey(), Boolean.TRUE.equals(item.getVisible())));
        PROFILE_FIELD_DEFINITIONS.forEach(definition -> upsertConfigValue(
                tenantId,
                definition.configKey(),
                definition.fieldLabel() + "展示开关",
                String.valueOf(requestedVisibility.getOrDefault(definition.fieldKey(), definition.defaultVisible())),
                definition.fieldDescription(),
                currentUser.getUserId()
        ));
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "profile-field", "update", "UPDATE", "SUCCESS", "更新个人中心字段展示设置");
        return loadProfileFieldSettings(tenantId);
    }

    private List<SystemVO.ProfileFieldSettingVO> loadProfileFieldSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, PROFILE_FIELD_CONFIG_KEYS);
        return PROFILE_FIELD_DEFINITIONS.stream().map(definition -> {
            SystemVO.ProfileFieldSettingVO item = new SystemVO.ProfileFieldSettingVO();
            item.setFieldKey(definition.fieldKey());
            item.setFieldLabel(definition.fieldLabel());
            item.setFieldDescription(definition.fieldDescription());
            item.setVisible(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(definition.configKey()), String.valueOf(definition.defaultVisible()))));
            return item;
        }).toList();
    }

    private Map<String, String> loadConfigValuesByKeys(Long tenantId, List<String> keys) {
        Long effectiveTenantId = tenantId == null ? DEFAULT_PUBLIC_TENANT_ID : tenantId;
        String placeholders = keys.stream().map(item -> "?").collect(Collectors.joining(", "));
        String sql = """
                select tenant_id as tenantId, config_key as configKey, config_value as configValue
                from sys_config
                where deleted = 0
                  and config_scope = 'PLATFORM'
                  and config_key in (%s)
                  and (tenant_id = ? or tenant_id is null)
                order by case when tenant_id = ? then 0 else 1 end, id desc
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(keys);
        params.add(effectiveTenantId);
        params.add(effectiveTenantId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<String, String> valueByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String configKey = String.valueOf(row.get("configKey"));
            if (!valueByKey.containsKey(configKey)) {
                valueByKey.put(configKey, normalizeConfigText(row.get("configValue")));
            }
        }
        return valueByKey;
    }

    private void upsertConfigValue(
            Long tenantId,
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
        Long existingId = queryConfigId(configKey, tenantId);
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_config (
                                tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, 'PLATFORM', 0, ?, ?, ?, 0)
                            """,
                    tenantId,
                    configKey,
                    configName,
                    configValue,
                    remark,
                    operatorId,
                    operatorId
            );
            return;
        }
        jdbcTemplate.update(
                """
                        update sys_config
                        set config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                            updated_by = ?, updated_at = ?, deleted = 0
                        where id = ?
                        """,
                configName,
                configValue,
                remark,
                operatorId,
                LocalDateTime.now(),
                existingId
        );
    }

    private Long queryConfigId(String configKey, Long tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id
                            from sys_config
                            where config_key = ? and tenant_id <=> ?
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    configKey,
                    tenantId
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            return DEFAULT_PUBLIC_TENANT_ID;
        }
        return currentUser.getCurrentTenantId();
    }

    private static final class ProfileFieldDefinition {
        private final String fieldKey;
        private final String fieldLabel;
        private final String fieldDescription;
        private final String configKey;
        private final boolean defaultVisible;

        private ProfileFieldDefinition(String fieldKey, String fieldLabel, String fieldDescription, String configKey, boolean defaultVisible) {
            this.fieldKey = fieldKey;
            this.fieldLabel = fieldLabel;
            this.fieldDescription = fieldDescription;
            this.configKey = configKey;
            this.defaultVisible = defaultVisible;
        }

        private String fieldKey() {
            return fieldKey;
        }

        private String fieldLabel() {
            return fieldLabel;
        }

        private String fieldDescription() {
            return fieldDescription;
        }

        private String configKey() {
            return configKey;
        }

        private boolean defaultVisible() {
            return defaultVisible;
        }
    }
}
