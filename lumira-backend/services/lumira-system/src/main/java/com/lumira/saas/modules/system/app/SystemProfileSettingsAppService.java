package com.lumira.saas.modules.system.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.profile.dto.ProfileFieldSettingItem;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionGroupVO;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionItemVO;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionSummaryVO;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.dao.EmptyResultDataAccessException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SystemProfileSettingsAppService {

    private static final Long DEFAULT_PUBLIC_TENANT_ID = com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    private static final long PROFILE_SETTINGS_CACHE_TTL_MS = 30_000L;
    private static final int PROFILE_SETTINGS_CACHE_MAX_ENTRIES = 2048;
    private static final Integer PROFILE_SCORE_MAX = 100;
    private static final String PROFILE_FIELD_GROUP_BASIC_KEY = "basic";
    private static final String PROFILE_FIELD_GROUP_CONTACT_KEY = "contact";
    private static final String PROFILE_FIELD_GROUP_IDENTITY_KEY = "identity";
    private static final String PROFILE_FIELD_GROUP_CUSTOM_KEY = "custom";
    private static final String PROFILE_FIELD_GROUP_CUSTOM_LABEL = "自定义资料";
    private static final String CUSTOM_PROFILE_FIELD_DEFINITIONS_KEY = "profile.field.custom.definitions";
    private static final Set<String> SUPPORTED_CUSTOM_FIELD_TYPES = Set.of("TEXT", "NUMBER", "DATE", "SELECT", "TEXTAREA");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<ProfileFieldDefinition> PROFILE_FIELD_DEFINITIONS = List.of(
            new ProfileFieldDefinition("avatarUrl", "头像", "控制个人中心是否展示头像上传与预览区域", PROFILE_FIELD_GROUP_BASIC_KEY, "基础资料", "profile.field.avatar.visible", "profile.field.avatar.weight", true, 10, "IMAGE", false, null, 10, false),
            new ProfileFieldDefinition("realName", "姓名", "控制个人中心是否展示姓名字段", PROFILE_FIELD_GROUP_BASIC_KEY, "基础资料", "profile.field.real-name.visible", "profile.field.real-name.weight", true, 15, "TEXT", false, "请输入姓名", 20, false),
            new ProfileFieldDefinition("mobile", "手机号", "控制个人中心是否展示手机号字段", PROFILE_FIELD_GROUP_CONTACT_KEY, "联系方式", "profile.field.mobile.visible", "profile.field.mobile.weight", true, 15, "MOBILE", false, "请输入手机号", 30, false),
            new ProfileFieldDefinition("email", "邮箱", "控制个人中心是否展示邮箱字段", PROFILE_FIELD_GROUP_CONTACT_KEY, "联系方式", "profile.field.email.visible", "profile.field.email.weight", true, 15, "EMAIL", false, "请输入邮箱", 40, false),
            new ProfileFieldDefinition("birthMonth", "出生年月", "控制个人中心是否展示出生年月字段", PROFILE_FIELD_GROUP_BASIC_KEY, "基础资料", "profile.field.birth-month.visible", "profile.field.birth-month.weight", true, 10, "MONTH", false, "请选择出生年月", 50, false),
            new ProfileFieldDefinition("gender", "性别", "控制个人中心是否展示性别字段", PROFILE_FIELD_GROUP_BASIC_KEY, "基础资料", "profile.field.gender.visible", "profile.field.gender.weight", true, 10, "SELECT", false, "请选择性别", 60, false),
            new ProfileFieldDefinition("region", "所在地区", "控制个人中心是否展示所在地区字段", PROFILE_FIELD_GROUP_BASIC_KEY, "基础资料", "profile.field.region.visible", "profile.field.region.weight", true, 10, "TEXT", false, "请输入所在地区", 70, false),
            new ProfileFieldDefinition("idCardNumber", "身份证号码", "控制个人中心是否展示身份证号码字段", PROFILE_FIELD_GROUP_IDENTITY_KEY, "证件信息", "profile.field.id-card-number.visible", "profile.field.id-card-number.weight", true, 5, "ID_CARD", false, "请输入身份证号码", 80, false)
    );
    private static final List<String> PROFILE_FIELD_CONFIG_KEYS = PROFILE_FIELD_DEFINITIONS.stream()
            .flatMap(definition -> List.of(definition.visibleConfigKey(), definition.weightConfigKey()).stream())
            .collect(Collectors.collectingAndThen(Collectors.toCollection(ArrayList::new), keys -> {
                keys.add(CUSTOM_PROFILE_FIELD_DEFINITIONS_KEY);
                return List.copyOf(keys);
            }));

    private final MyBatisQueryOperations jdbcTemplate;
    private final OperationAuditService operationAuditService;
    private final Cache<Long, List<ProfileFieldSettingVO>> profileFieldSettingsCache;
    private final Cache<Long, CompletableFuture<List<ProfileFieldSettingVO>>> profileFieldSettingsLoadInFlight;

    public SystemProfileSettingsAppService(MyBatisQueryOperations jdbcTemplate, OperationAuditService operationAuditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationAuditService = operationAuditService;
        this.profileFieldSettingsCache = CacheBuilder.newBuilder()
                .maximumSize(PROFILE_SETTINGS_CACHE_MAX_ENTRIES)
                .expireAfterWrite(PROFILE_SETTINGS_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.profileFieldSettingsLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(PROFILE_SETTINGS_CACHE_MAX_ENTRIES)
                .expireAfterWrite(PROFILE_SETTINGS_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    public List<ProfileFieldSettingVO> getProfileFieldSettings(CurrentUser currentUser) {
        return loadProfileFieldSettings(currentTenantId(currentUser));
    }

    @Transactional
    public List<ProfileFieldSettingVO> updateProfileFieldSettings(CurrentUser currentUser, SystemDTO.ProfileFieldSettingsRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Map<String, ProfileFieldSettingItem> requestedSettings = new LinkedHashMap<>();
        request.getItems().forEach(item -> requestedSettings.put(item.getFieldKey(), item));
        List<ProfileFieldDefinition> customDefinitions = normalizeCustomDefinitions(request.getItems(), requestedSettings.keySet());
        PROFILE_FIELD_DEFINITIONS.forEach(definition -> upsertConfigValue(
                tenantId,
                definition.visibleConfigKey(),
                definition.fieldLabel() + "展示开关",
                String.valueOf(requestedVisibility(requestedSettings.get(definition.fieldKey()), definition.defaultVisible())),
                definition.fieldDescription(),
                currentUser.getUserId()
        ));
        PROFILE_FIELD_DEFINITIONS.forEach(definition -> upsertConfigValue(
                tenantId,
                definition.weightConfigKey(),
                definition.fieldLabel() + "评分权重",
                String.valueOf(resolveRequestedWeight(requestedSettings.get(definition.fieldKey()), definition.defaultWeight())),
                definition.fieldDescription(),
                currentUser.getUserId()
        ));
        upsertConfigValue(
                tenantId,
                CUSTOM_PROFILE_FIELD_DEFINITIONS_KEY,
                "自定义资料字段定义",
                serializeCustomDefinitions(customDefinitions),
                "保存个人中心可扩展的自定义资料字段定义",
                currentUser.getUserId()
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "profile-field", "update", "UPDATE", "SUCCESS", "更新个人中心字段展示设置");
        invalidateProfileFieldSettingsCache(tenantId);
        return loadProfileFieldSettings(tenantId);
    }

    private List<ProfileFieldSettingVO> loadProfileFieldSettings(Long tenantId) {
        List<ProfileFieldSettingVO> cached = profileFieldSettingsCache.getIfPresent(tenantId);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        return loadProfileFieldSettingsWithSingleFlight(tenantId);
    }

    private List<ProfileFieldSettingVO> loadProfileFieldSettingsWithSingleFlight(Long tenantId) {
        try {
            CompletableFuture<List<ProfileFieldSettingVO>> future = profileFieldSettingsLoadInFlight.get(
                    tenantId,
                    () -> CompletableFuture.completedFuture(loadProfileFieldSettingsFresh(tenantId))
            );
            List<ProfileFieldSettingVO> settings = future.join();
            profileFieldSettingsLoadInFlight.invalidate(tenantId);
            return settings;
        } catch (ExecutionException ex) {
            profileFieldSettingsLoadInFlight.invalidate(tenantId);
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load profile field settings", cause);
        } catch (RuntimeException ex) {
            profileFieldSettingsLoadInFlight.invalidate(tenantId);
            throw ex;
        }
    }

    private List<ProfileFieldSettingVO> loadProfileFieldSettingsFresh(Long tenantId) {
        List<ProfileFieldSettingVO> cached = profileFieldSettingsCache.getIfPresent(tenantId);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, PROFILE_FIELD_CONFIG_KEYS);
        List<ProfileFieldDefinition> definitions = new ArrayList<>(PROFILE_FIELD_DEFINITIONS);
        definitions.addAll(parseCustomDefinitions(valueByKey.get(CUSTOM_PROFILE_FIELD_DEFINITIONS_KEY)));
        List<ProfileFieldSettingVO> settings = definitions.stream()
                .sorted(Comparator.comparing(ProfileFieldDefinition::sortNo).thenComparing(ProfileFieldDefinition::fieldKey))
                .map(definition -> {
            ProfileFieldSettingVO item = new ProfileFieldSettingVO();
            item.setFieldKey(definition.fieldKey());
            item.setFieldLabel(definition.fieldLabel());
            item.setFieldDescription(definition.fieldDescription());
            item.setGroupKey(definition.groupKey());
            item.setGroupLabel(definition.groupLabel());
            item.setVisible(definition.custom()
                    ? definition.defaultVisible()
                    : Boolean.parseBoolean(defaultIfBlank(valueByKey.get(definition.visibleConfigKey()), String.valueOf(definition.defaultVisible()))));
            item.setWeight(definition.custom()
                    ? definition.defaultWeight()
                    : parseInteger(defaultIfBlank(valueByKey.get(definition.weightConfigKey()), String.valueOf(definition.defaultWeight())), definition.defaultWeight()));
            item.setFieldType(definition.fieldType());
            item.setRequired(definition.required());
            item.setPlaceholder(definition.placeholder());
            item.setSortNo(definition.sortNo());
            item.setCustom(definition.custom());
            return item;
        }).toList();
        profileFieldSettingsCache.put(tenantId, new ArrayList<>(settings));
        return settings;
    }

    private void invalidateProfileFieldSettingsCache(Long tenantId) {
        profileFieldSettingsCache.invalidate(tenantId);
        profileFieldSettingsLoadInFlight.invalidate(tenantId);
    }

    public ProfileCompletionSummaryVO buildProfileCompletionSummary(
            CurrentUserVO currentUser,
            List<ProfileFieldSettingVO> profileFieldSettings,
            boolean mobileBindAvailable,
            boolean emailBindAvailable
    ) {
        Map<String, ProfileFieldSettingVO> settingByFieldKey = profileFieldSettings == null
                ? Map.of()
                : profileFieldSettings.stream().collect(Collectors.toMap(
                        ProfileFieldSettingVO::getFieldKey,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<EvaluatedField> evaluatedFields = new ArrayList<>();
        int totalWeight = 0;
        for (ProfileFieldDefinition definition : PROFILE_FIELD_DEFINITIONS) {
            ProfileFieldSettingVO setting = settingByFieldKey.get(definition.fieldKey());
            if (setting == null || !Boolean.TRUE.equals(setting.getVisible())) {
                continue;
            }
            int weight = resolveRequestedWeight(setting.getWeight(), definition.defaultWeight());
            totalWeight += weight;
            evaluatedFields.add(new EvaluatedField(definition, weight));
        }

        List<ProfileCompletionGroupVO> groups = new ArrayList<>();
        List<ProfileCompletionItemVO> incompleteItems = new ArrayList<>();
        Map<String, GroupAccumulator> groupByKey = new LinkedHashMap<>();
        int earnedWeight = 0;
        for (EvaluatedField field : evaluatedFields) {
            boolean completed = isProfileFieldCompleted(currentUser, field.definition.fieldKey());
            int scoreContribution = totalWeight > 0 ? Math.round((field.weight * 100.0f) / totalWeight) : 0;
            if (completed) {
                earnedWeight += field.weight;
            }

            ProfileCompletionItemVO item = new ProfileCompletionItemVO();
            item.setFieldKey(field.definition.fieldKey());
            item.setFieldLabel(field.definition.fieldLabel());
            item.setFieldDescription(field.definition.fieldDescription());
            item.setGroupKey(field.definition.groupKey());
            item.setGroupLabel(field.definition.groupLabel());
            item.setCompleted(completed);
            item.setWeight(field.weight);
            item.setScoreContribution(completed ? scoreContribution : 0);
            item.setValueText(resolveProfileFieldValue(currentUser, field.definition.fieldKey(), completed));
            item.setActionType(completed ? null : resolveActionType(field.definition.fieldKey()));
            item.setActionAvailable(completed ? false : resolveActionAvailable(field.definition.fieldKey(), mobileBindAvailable, emailBindAvailable));
            item.setActionTarget(completed ? null : resolveActionTarget(field.definition.fieldKey()));
            item.setActionLabel(completed ? null : resolveActionLabel(field.definition.fieldKey(), mobileBindAvailable, emailBindAvailable));
            item.setActionHint(completed ? null : resolveActionHint(field.definition.fieldKey(), mobileBindAvailable, emailBindAvailable, scoreContribution));

            GroupAccumulator groupAccumulator = groupByKey.computeIfAbsent(
                    field.definition.groupKey(),
                    key -> new GroupAccumulator(field.definition.groupKey(), field.definition.groupLabel())
            );
            groupAccumulator.totalWeight += field.weight;
            if (completed) {
                groupAccumulator.earnedWeight += field.weight;
            }
            groupAccumulator.items.add(item);
            if (!completed) {
                incompleteItems.add(item);
            }
        }

        for (GroupAccumulator groupAccumulator : groupByKey.values()) {
            ProfileCompletionGroupVO group = new ProfileCompletionGroupVO();
            group.setGroupKey(groupAccumulator.groupKey);
            group.setGroupLabel(groupAccumulator.groupLabel);
            group.setTotalWeight(groupAccumulator.totalWeight);
            group.setEarnedWeight(groupAccumulator.earnedWeight);
            group.setCompletionRate(resolveCompletionRate(groupAccumulator.earnedWeight, groupAccumulator.totalWeight));
            group.setScore(group.getCompletionRate());
            group.setMaxScore(PROFILE_SCORE_MAX);
            group.setItems(new ArrayList<>(groupAccumulator.items));
            groups.add(group);
        }

        ProfileCompletionSummaryVO summary = new ProfileCompletionSummaryVO();
        summary.setTotalWeight(totalWeight);
        summary.setEarnedWeight(earnedWeight);
        summary.setCompletionRate(resolveCompletionRate(earnedWeight, totalWeight));
        summary.setScore(summary.getCompletionRate());
        summary.setMaxScore(PROFILE_SCORE_MAX);
        summary.setGroups(groups);
        summary.setIncompleteItems(incompleteItems);
        return summary;
    }

    private List<ProfileFieldDefinition> normalizeCustomDefinitions(List<ProfileFieldSettingItem> items, Set<String> requestedKeys) {
        Map<String, ProfileFieldDefinition> builtInByKey = PROFILE_FIELD_DEFINITIONS.stream()
                .collect(Collectors.toMap(ProfileFieldDefinition::fieldKey, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<ProfileFieldDefinition> customDefinitions = new ArrayList<>();
        Set<String> usedKeys = new java.util.HashSet<>(builtInByKey.keySet());
        int fallbackSortNo = 1000;
        for (ProfileFieldSettingItem item : items) {
            if (item == null || !Boolean.TRUE.equals(item.getCustom())) {
                continue;
            }
            String fieldKey = normalizeCustomFieldKey(item.getFieldKey());
            if (!StringUtils.hasText(fieldKey) || usedKeys.contains(fieldKey)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "自定义字段标识不可为空或重复");
            }
            if (!requestedKeys.contains(item.getFieldKey())) {
                continue;
            }
            String fieldLabel = normalizeLimitedText(item.getFieldLabel(), 64);
            if (!StringUtils.hasText(fieldLabel)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "自定义字段名称不能为空");
            }
            String fieldType = normalizeCustomFieldType(item.getFieldType());
            int weight = resolveRequestedWeight(item, 5);
            int sortNo = item.getSortNo() == null ? fallbackSortNo : Math.max(1, item.getSortNo());
            fallbackSortNo = sortNo + 10;
            customDefinitions.add(new ProfileFieldDefinition(
                    fieldKey,
                    fieldLabel,
                    normalizeLimitedText(item.getFieldDescription(), 200),
                    defaultIfBlank(normalizeCustomFieldKey(item.getGroupKey()), PROFILE_FIELD_GROUP_CUSTOM_KEY),
                    defaultIfBlank(normalizeLimitedText(item.getGroupLabel(), 64), PROFILE_FIELD_GROUP_CUSTOM_LABEL),
                    null,
                    null,
                    requestedVisibility(item, true),
                    weight,
                    fieldType,
                    Boolean.TRUE.equals(item.getRequired()),
                    normalizeLimitedText(item.getPlaceholder(), 120),
                    sortNo,
                    true
            ));
            usedKeys.add(fieldKey);
        }
        return customDefinitions;
    }

    private List<ProfileFieldDefinition> parseCustomDefinitions(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<ProfileFieldSettingItem> items = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
            return normalizeCustomDefinitions(items, items.stream().map(ProfileFieldSettingItem::getFieldKey).collect(Collectors.toSet()));
        } catch (JsonProcessingException | RuntimeException exception) {
            return List.of();
        }
    }

    private String serializeCustomDefinitions(List<ProfileFieldDefinition> definitions) {
        List<ProfileFieldSettingItem> items = definitions.stream().map(definition -> {
            ProfileFieldSettingItem item = new ProfileFieldSettingItem();
            item.setFieldKey(definition.fieldKey());
            item.setFieldLabel(definition.fieldLabel());
            item.setFieldDescription(definition.fieldDescription());
            item.setGroupKey(definition.groupKey());
            item.setGroupLabel(definition.groupLabel());
            item.setVisible(definition.defaultVisible());
            item.setWeight(definition.defaultWeight());
            item.setFieldType(definition.fieldType());
            item.setRequired(definition.required());
            item.setPlaceholder(definition.placeholder());
            item.setSortNo(definition.sortNo());
            item.setCustom(true);
            return item;
        }).toList();
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "自定义字段定义序列化失败");
        }
    }

    private String normalizeCustomFieldKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("[^A-Za-z0-9_]", "");
    }

    private String normalizeCustomFieldType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : "TEXT";
        return SUPPORTED_CUSTOM_FIELD_TYPES.contains(normalized) ? normalized : "TEXT";
    }

    private String normalizeLimitedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
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

    private Integer parseInteger(String value, Integer fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean requestedVisibility(ProfileFieldSettingItem item, boolean fallback) {
        if (item == null || item.getVisible() == null) {
            return fallback;
        }
        return item.getVisible();
    }

    private int resolveCompletionRate(int earnedWeight, int totalWeight) {
        if (totalWeight <= 0) {
            return 0;
        }
        return Math.round((earnedWeight * 100.0f) / totalWeight);
    }

    private int resolveRequestedWeight(ProfileFieldSettingItem item, int fallback) {
        if (item == null || item.getWeight() == null || item.getWeight() <= 0) {
            return fallback;
        }
        return item.getWeight();
    }

    private int resolveRequestedWeight(Integer weight, int fallback) {
        if (weight == null || weight <= 0) {
            return fallback;
        }
        return weight;
    }

    private boolean isProfileFieldCompleted(CurrentUserVO currentUser, String fieldKey) {
        if (currentUser == null) {
            return false;
        }
        return switch (fieldKey) {
            case "avatarUrl" -> StringUtils.hasText(currentUser.getAvatarUrl());
            case "realName" -> StringUtils.hasText(currentUser.getRealName());
            case "mobile" -> StringUtils.hasText(currentUser.getMobile());
            case "email" -> StringUtils.hasText(currentUser.getEmail());
            case "birthMonth" -> StringUtils.hasText(currentUser.getBirthMonth());
            case "gender" -> StringUtils.hasText(currentUser.getGender());
            case "region" -> StringUtils.hasText(currentUser.getRegion());
            case "idCardNumber" -> StringUtils.hasText(currentUser.getIdCardNumber());
            default -> currentUser.getExtraProfileValues() != null
                    && StringUtils.hasText(currentUser.getExtraProfileValues().get(fieldKey));
        };
    }

    private String resolveProfileFieldValue(CurrentUserVO currentUser, String fieldKey, boolean completed) {
        if (currentUser == null) {
            return "-";
        }
        return switch (fieldKey) {
            case "avatarUrl" -> completed ? "已上传" : "未上传";
            case "realName" -> defaultIfBlank(currentUser.getRealName(), "-");
            case "mobile" -> maskMobile(currentUser.getMobile());
            case "email" -> maskEmail(currentUser.getEmail());
            case "birthMonth" -> defaultIfBlank(currentUser.getBirthMonth(), "-");
            case "gender" -> defaultIfBlank(currentUser.getGender(), "-");
            case "region" -> defaultIfBlank(currentUser.getRegion(), "-");
            case "idCardNumber" -> maskIdCardNumber(currentUser.getIdCardNumber());
            default -> currentUser.getExtraProfileValues() == null
                    ? "-"
                    : defaultIfBlank(currentUser.getExtraProfileValues().get(fieldKey), "-");
        };
    }

    private String resolveActionType(String fieldKey) {
        if ("mobile".equals(fieldKey) || "email".equals(fieldKey)) {
            return "CONTACT_BIND";
        }
        return "PROFILE_FIELD";
    }

    private boolean resolveActionAvailable(String fieldKey, boolean mobileBindAvailable, boolean emailBindAvailable) {
        if ("mobile".equals(fieldKey)) {
            return mobileBindAvailable;
        }
        if ("email".equals(fieldKey)) {
            return emailBindAvailable;
        }
        return true;
    }

    private String resolveActionTarget(String fieldKey) {
        return fieldKey;
    }

    private String resolveActionLabel(String fieldKey, boolean mobileBindAvailable, boolean emailBindAvailable) {
        if ("avatarUrl".equals(fieldKey)) {
            return "去上传";
        }
        if ("mobile".equals(fieldKey)) {
            return mobileBindAvailable ? "去绑定" : "待开启";
        }
        if ("email".equals(fieldKey)) {
            return emailBindAvailable ? "去绑定" : "待开启";
        }
        return "去完善";
    }

    private String resolveActionHint(String fieldKey, boolean mobileBindAvailable, boolean emailBindAvailable, int scoreContribution) {
        if ("mobile".equals(fieldKey) && !mobileBindAvailable) {
            return "请先开启短信验证后再补全";
        }
        if ("email".equals(fieldKey) && !emailBindAvailable) {
            return "请先开启邮箱验证后再补全";
        }
        return scoreContribution > 0 ? "预计提升 +" + scoreContribution + " 分" : null;
    }

    private String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return "-";
        }
        String normalized = mobile.trim();
        return normalized.length() >= 7 ? normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4) : normalized;
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "-";
        }
        String normalized = email.trim();
        int atIndex = normalized.indexOf('@');
        if (atIndex <= 0) {
            return normalized;
        }
        String localPart = normalized.substring(0, atIndex);
        String domainPart = normalized.substring(atIndex + 1);
        if (localPart.length() <= 2) {
            return "**@" + domainPart;
        }
        return localPart.substring(0, 2) + "***@" + domainPart;
    }

    private String maskIdCardNumber(String idCardNumber) {
        if (!StringUtils.hasText(idCardNumber)) {
            return "-";
        }
        String normalized = idCardNumber.trim();
        if (normalized.length() <= 8) {
            return normalized;
        }
        return normalized.substring(0, 3) + "********" + normalized.substring(normalized.length() - 4);
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long currentTenantId(CurrentUser currentUser) {
        return DEFAULT_PUBLIC_TENANT_ID;
    }

    private static final class EvaluatedField {
        private final ProfileFieldDefinition definition;
        private final int weight;

        private EvaluatedField(ProfileFieldDefinition definition, int weight) {
            this.definition = definition;
            this.weight = weight;
        }
    }

    private static final class GroupAccumulator {
        private final String groupKey;
        private final String groupLabel;
        private final List<ProfileCompletionItemVO> items = new ArrayList<>();
        private int totalWeight;
        private int earnedWeight;

        private GroupAccumulator(String groupKey, String groupLabel) {
            this.groupKey = groupKey;
            this.groupLabel = groupLabel;
        }
    }

    private static final class ProfileFieldDefinition {
        private final String fieldKey;
        private final String fieldLabel;
        private final String fieldDescription;
        private final String groupKey;
        private final String groupLabel;
        private final String visibleConfigKey;
        private final String weightConfigKey;
        private final boolean defaultVisible;
        private final int defaultWeight;
        private final String fieldType;
        private final boolean required;
        private final String placeholder;
        private final int sortNo;
        private final boolean custom;

        private ProfileFieldDefinition(
                String fieldKey,
                String fieldLabel,
                String fieldDescription,
                String groupKey,
                String groupLabel,
                String visibleConfigKey,
                String weightConfigKey,
                boolean defaultVisible,
                int defaultWeight,
                String fieldType,
                boolean required,
                String placeholder,
                int sortNo,
                boolean custom
        ) {
            this.fieldKey = fieldKey;
            this.fieldLabel = fieldLabel;
            this.fieldDescription = fieldDescription;
            this.groupKey = groupKey;
            this.groupLabel = groupLabel;
            this.visibleConfigKey = visibleConfigKey;
            this.weightConfigKey = weightConfigKey;
            this.defaultVisible = defaultVisible;
            this.defaultWeight = defaultWeight;
            this.fieldType = fieldType;
            this.required = required;
            this.placeholder = placeholder;
            this.sortNo = sortNo;
            this.custom = custom;
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

        private String groupKey() {
            return groupKey;
        }

        private String groupLabel() {
            return groupLabel;
        }

        private String visibleConfigKey() {
            return visibleConfigKey;
        }

        private String weightConfigKey() {
            return weightConfigKey;
        }

        private boolean defaultVisible() {
            return defaultVisible;
        }

        private int defaultWeight() {
            return defaultWeight;
        }

        private String fieldType() {
            return fieldType;
        }

        private boolean required() {
            return required;
        }

        private String placeholder() {
            return placeholder;
        }

        private int sortNo() {
            return sortNo;
        }

        private boolean custom() {
            return custom;
        }
    }
}
