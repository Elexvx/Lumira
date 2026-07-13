package com.lumira.saas.modules.system.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.profile.dto.ProfileFieldSettingItem;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionGroupVO;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionItemVO;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionSummaryVO;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import com.lumira.saas.modules.system.profile.repository.SystemProfileSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemProfileSettingsAppService {

    public static final String PROFILE_PAGE_KEY = "PROFILE";
    public static final String TEAM_MEMBER_PAGE_KEY = "TEAM_MEMBER";
    private static final String DICT_SUPPORTED_PAGE_KEYS = "profile_settings_page_key";
    private static final String DICT_CUSTOM_FIELD_TYPES = "profile_custom_field_type";
    private static final Integer PROFILE_SCORE_MAX = 100;
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String PROFILE_FIELD_GROUP_BASIC_KEY = "basic";
    private static final String PROFILE_FIELD_GROUP_CONTACT_KEY = "contact";
    private static final String PROFILE_FIELD_GROUP_IDENTITY_KEY = "identity";
    private static final String PROFILE_FIELD_GROUP_CUSTOM_KEY = "custom";
    private static final String PROFILE_FIELD_GROUP_CUSTOM_LABEL = "Custom profile";
    private static final String SYSTEM_PROFILE_FIELD_OVERRIDES_KEY = "profile.field.system.overrides";
    private static final String CUSTOM_PROFILE_FIELD_DEFINITIONS_KEY = "profile.field.custom.definitions";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SystemProfileSettingsRepository repository;
    private final OperationAuditService operationAuditService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public SystemProfileSettingsAppService(
            SystemProfileSettingsRepository repository,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(repository, operationAuditService, permissionSnapshotService, null, null, false);
    }

    @Autowired
    public SystemProfileSettingsAppService(
            SystemProfileSettingsRepository repository,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(repository, operationAuditService, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private SystemProfileSettingsAppService(
            SystemProfileSettingsRepository repository,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.repository = repository;
        this.operationAuditService = operationAuditService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public SystemProfileSettingsAppService(
            SystemProfileSettingsRepository repository,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(repository, operationAuditService, permissionSnapshotService, null, sessionAuthenticationService, false);
    }

    public SystemProfileSettingsAppService(SystemProfileSettingsRepository repository, OperationAuditService operationAuditService) {
        this(repository, operationAuditService, null, null, null, false);
    }

    public List<ProfileFieldSettingVO> getProfileFieldSettings(CurrentUser currentUser) {
        requireAuthenticated(currentUser);
        return loadProfileFieldSettings(normalizePageKey(PROFILE_PAGE_KEY));
    }

    public List<ProfileFieldSettingVO> getProfileFieldSettingsForManagement(CurrentUser currentUser, String pageKey) {
        requirePermission(currentUser, "system:config:view");
        return loadProfileFieldSettings(normalizePageKey(pageKey));
    }

    @Transactional
    public List<ProfileFieldSettingVO> updateProfileFieldSettings(CurrentUser currentUser, SystemDTO.ProfileFieldSettingsRequest request) {
        requireRequest(request, "Profile field settings request is required");
        return updateProfileFieldSettings(currentUser, request, request.getPageKey());
    }

    @Transactional
    public List<ProfileFieldSettingVO> updateProfileFieldSettings(CurrentUser currentUser, SystemDTO.ProfileFieldSettingsRequest request, String pageKey) {
        requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Profile field settings request is required");
        String operatorUuid = currentUser.getUserUuid();
        String normalizedPageKey = normalizePageKey(pageKey);
        List<ProfileFieldDefinition> builtInDefinitions = builtInDefinitions(normalizedPageKey);
        Map<String, ProfileFieldSettingItem> requestedSettings = new LinkedHashMap<>();
        request.getItems().forEach(item -> requestedSettings.put(item.getFieldKey(), item));
        List<ProfileFieldMetadataOverride> systemOverrides = normalizeSystemFieldOverrides(request.getItems(), builtInDefinitions);
        List<ProfileFieldDefinition> customDefinitions = normalizeCustomDefinitions(request.getItems(), requestedSettings.keySet(), builtInDefinitions, normalizedPageKey);
        builtInDefinitions.forEach(definition -> upsertConfigValue(
                definition.visibleConfigKey(),
                definition.fieldLabel() + " visibility",
                String.valueOf(requestedVisibility(requestedSettings.get(definition.fieldKey()), definition.defaultVisible())),
                definition.fieldDescription(),
                currentUser.getUserId(),
                operatorUuid
        ));
        builtInDefinitions.forEach(definition -> upsertConfigValue(
                definition.weightConfigKey(),
                definition.fieldLabel() + "评分权重",
                String.valueOf(resolveRequestedWeight(requestedSettings.get(definition.fieldKey()), definition.defaultWeight())),
                definition.fieldDescription(),
                currentUser.getUserId(),
                operatorUuid
        ));
        builtInDefinitions.forEach(definition -> upsertConfigValue(
                definition.requiredConfigKey(),
                definition.fieldLabel() + " required",
                String.valueOf(requestedRequired(requestedSettings.get(definition.fieldKey()), definition.required())),
                definition.fieldDescription(),
                currentUser.getUserId(),
                operatorUuid
        ));
        builtInDefinitions.forEach(definition -> upsertConfigValue(
                definition.sortConfigKey(),
                definition.fieldLabel() + " sort",
                String.valueOf(resolveRequestedSortNo(requestedSettings.get(definition.fieldKey()), definition.sortNo())),
                definition.fieldDescription(),
                currentUser.getUserId(),
                operatorUuid
        ));
        upsertConfigValue(
                systemOverridesConfigKey(normalizedPageKey),
                "System profile field metadata overrides",
                serializeSystemFieldOverrides(systemOverrides),
                "Stores editable labels, descriptions, placeholders, and groups for built-in profile fields",
                currentUser.getUserId(),
                operatorUuid
        );
        upsertConfigValue(
                customDefinitionsConfigKey(normalizedPageKey),
                "Custom profile field definitions",
                serializeCustomDefinitions(customDefinitions),
                "Stores custom profile field definitions",
                currentUser.getUserId(),
                operatorUuid
        );
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "profile-field", "update", "UPDATE", "SUCCESS", "更新个人中心字段展示设置");
        return loadProfileFieldSettings(normalizedPageKey);
    }

    private Long requireAuthenticated(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户上下文不可信");
        }
        return currentUser.getUserId();
    }

    private Long requirePermission(CurrentUser currentUser, String permission) {
        Long userId = requireAuthenticated(currentUser);
        if (currentUser.getPermissions() == null || !currentUser.getPermissions().contains(permission)) {
            throw new BizException(ErrorCode.FORBIDDEN, "缺少权限: " + permission);
        }
        return userId;
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(currentUsername);
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        return refreshedUser;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private void requireRequest(Object request, String message) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private List<ProfileFieldSettingVO> loadProfileFieldSettings(String pageKey) {
        List<ProfileFieldDefinition> builtInDefinitions = builtInDefinitions(pageKey);
        Map<String, String> valueByKey = loadConfigValuesByKeys(fieldConfigKeys(pageKey, builtInDefinitions));
        Map<String, ProfileFieldMetadataOverride> systemOverrides = parseSystemFieldOverrides(valueByKey.get(systemOverridesConfigKey(pageKey)), builtInDefinitions);
        List<ProfileFieldDefinition> definitions = new ArrayList<>(builtInDefinitions);
        definitions.addAll(parseCustomDefinitions(valueByKey.get(customDefinitionsConfigKey(pageKey)), builtInDefinitions, pageKey));
        List<ProfileFieldSettingVO> settings = definitions.stream()
                .sorted(Comparator.comparing(ProfileFieldDefinition::sortNo).thenComparing(ProfileFieldDefinition::fieldKey))
                .map(definition -> {
            ProfileFieldMetadataOverride override = definition.custom() ? null : systemOverrides.get(definition.fieldKey());
            ProfileFieldSettingVO item = new ProfileFieldSettingVO();
            item.setFieldKey(definition.fieldKey());
            item.setPageKey(pageKey);
            item.setFieldLabel(overrideText(override == null ? null : override.fieldLabel(), definition.fieldLabel()));
            item.setFieldDescription(overrideText(override == null ? null : override.fieldDescription(), definition.fieldDescription()));
            item.setGroupKey(definition.groupKey());
            item.setGroupLabel(overrideText(override == null ? null : override.groupLabel(), definition.groupLabel()));
            item.setVisible(definition.custom()
                    ? definition.defaultVisible()
                    : Boolean.parseBoolean(defaultIfBlank(valueByKey.get(definition.visibleConfigKey()), String.valueOf(definition.defaultVisible()))));
            item.setWeight(definition.custom()
                    ? definition.defaultWeight()
                    : parseInteger(defaultIfBlank(valueByKey.get(definition.weightConfigKey()), String.valueOf(definition.defaultWeight())), definition.defaultWeight()));
            item.setFieldType(definition.fieldType());
            item.setRequired(definition.custom()
                    ? definition.required()
                    : Boolean.parseBoolean(defaultIfBlank(valueByKey.get(definition.requiredConfigKey()), String.valueOf(definition.required()))));
            item.setPlaceholder(overrideText(override == null ? null : override.placeholder(), definition.placeholder()));
            item.setSortNo(definition.custom()
                    ? definition.sortNo()
                    : parseInteger(defaultIfBlank(valueByKey.get(definition.sortConfigKey()), String.valueOf(definition.sortNo())), definition.sortNo()));
            item.setCustom(definition.custom());
            return item;
        }).toList();
        return settings;
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
        for (ProfileFieldDefinition definition : builtInDefinitions(PROFILE_PAGE_KEY)) {
            ProfileFieldSettingVO setting = settingByFieldKey.get(definition.fieldKey());
            if (setting == null || !Boolean.TRUE.equals(setting.getVisible())) {
                continue;
            }
            int weight = resolveRequestedWeight(setting.getWeight(), definition.defaultWeight());
            totalWeight += weight;
            evaluatedFields.add(new EvaluatedField(definition, setting, weight));
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
            item.setFieldLabel(overrideText(field.setting.getFieldLabel(), field.definition.fieldLabel()));
            item.setFieldDescription(overrideText(field.setting.getFieldDescription(), field.definition.fieldDescription()));
            item.setGroupKey(overrideText(field.setting.getGroupKey(), field.definition.groupKey()));
            item.setGroupLabel(overrideText(field.setting.getGroupLabel(), field.definition.groupLabel()));
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
                    item.getGroupKey(),
                    key -> new GroupAccumulator(item.getGroupKey(), item.getGroupLabel())
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

    private List<ProfileFieldDefinition> normalizeCustomDefinitions(List<ProfileFieldSettingItem> items, Set<String> requestedKeys, List<ProfileFieldDefinition> builtInDefinitions, String pageKey) {
        Map<String, ProfileFieldDefinition> builtInByKey = builtInDefinitions.stream()
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
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Custom field label is required");
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

    private List<ProfileFieldMetadataOverride> normalizeSystemFieldOverrides(List<ProfileFieldSettingItem> items, List<ProfileFieldDefinition> builtInDefinitions) {
        Map<String, ProfileFieldDefinition> builtInByKey = builtInDefinitions.stream()
                .collect(Collectors.toMap(ProfileFieldDefinition::fieldKey, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<ProfileFieldMetadataOverride> overrides = new ArrayList<>();
        for (ProfileFieldSettingItem item : items) {
            if (item == null || Boolean.TRUE.equals(item.getCustom())) {
                continue;
            }
            ProfileFieldDefinition definition = builtInByKey.get(item.getFieldKey());
            if (definition == null) {
                continue;
            }
            String fieldLabel = normalizeLimitedText(item.getFieldLabel(), 64);
            String fieldDescription = normalizeLimitedText(item.getFieldDescription(), 200);
            String groupLabel = normalizeLimitedText(item.getGroupLabel(), 64);
            String placeholder = normalizeLimitedText(item.getPlaceholder(), 120);
            if (!StringUtils.hasText(fieldLabel)
                    && !StringUtils.hasText(fieldDescription)
                    && !StringUtils.hasText(groupLabel)
                    && !StringUtils.hasText(placeholder)) {
                continue;
            }
            overrides.add(new ProfileFieldMetadataOverride(
                    definition.fieldKey(),
                    fieldLabel,
                    fieldDescription,
                    groupLabel,
                    placeholder
            ));
        }
        return overrides;
    }

    private Map<String, ProfileFieldMetadataOverride> parseSystemFieldOverrides(String json, List<ProfileFieldDefinition> builtInDefinitions) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            List<ProfileFieldSettingItem> items = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
            return normalizeSystemFieldOverrides(items, builtInDefinitions).stream()
                    .collect(Collectors.toMap(ProfileFieldMetadataOverride::fieldKey, item -> item, (left, right) -> left, LinkedHashMap::new));
        } catch (JsonProcessingException | RuntimeException exception) {
            return Map.of();
        }
    }

    private String serializeSystemFieldOverrides(List<ProfileFieldMetadataOverride> overrides) {
        List<ProfileFieldSettingItem> items = overrides.stream().map(override -> {
            ProfileFieldSettingItem item = new ProfileFieldSettingItem();
            item.setFieldKey(override.fieldKey());
            item.setFieldLabel(override.fieldLabel());
            item.setFieldDescription(override.fieldDescription());
            item.setGroupLabel(override.groupLabel());
            item.setPlaceholder(override.placeholder());
            item.setCustom(false);
            return item;
        }).toList();
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "System profile field overrides serialization failed");
        }
    }

    private String overrideText(String override, String fallback) {
        return StringUtils.hasText(override) ? override : fallback;
    }

    private List<ProfileFieldDefinition> parseCustomDefinitions(String json, List<ProfileFieldDefinition> builtInDefinitions, String pageKey) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<ProfileFieldSettingItem> items = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
            return normalizeCustomDefinitions(items, items.stream().map(ProfileFieldSettingItem::getFieldKey).collect(Collectors.toSet()), builtInDefinitions, pageKey);
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
        List<String> supported = repository.findEnabledDictionaryValues(DICT_CUSTOM_FIELD_TYPES);
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : defaultCustomFieldType(supported);
        return supported.stream().anyMatch(normalized::equalsIgnoreCase) ? normalized : defaultCustomFieldType(supported);
    }

    private String normalizeLimitedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String normalizePageKey(String pageKey) {
        String normalized = StringUtils.hasText(pageKey) ? pageKey.trim().toUpperCase() : PROFILE_PAGE_KEY;
        return repository.findEnabledDictionaryValues(DICT_SUPPORTED_PAGE_KEYS).stream()
                .anyMatch(normalized::equalsIgnoreCase) ? normalized : PROFILE_PAGE_KEY;
    }

    private List<ProfileFieldDefinition> builtInDefinitions(String pageKey) {
        List<ProfileFieldDefinition> definitions = repository.findEnabledFieldDefinitions(pageKey).stream()
                .map(row -> new ProfileFieldDefinition(row.fieldKey(), row.fieldLabel(), row.fieldDescription(),
                        row.groupKey(), row.groupLabel(), row.visibleConfigKey(), row.weightConfigKey(),
                        row.defaultVisible(), row.defaultWeight(), row.fieldType(), row.required(),
                        row.placeholder(), row.sortNo(), false))
                .toList();
        if (definitions.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Profile field definitions are not configured: " + pageKey);
        }
        return definitions;
    }

    private String defaultCustomFieldType(List<String> supported) {
        return supported.stream().findFirst().orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR,
                "Profile custom field type dictionary is not configured"));
    }

    private String systemOverridesConfigKey(String pageKey) {
        return PROFILE_PAGE_KEY.equals(pageKey) ? SYSTEM_PROFILE_FIELD_OVERRIDES_KEY : pageKey.toLowerCase(Locale.ROOT) + ".field.system.overrides";
    }

    private String customDefinitionsConfigKey(String pageKey) {
        return PROFILE_PAGE_KEY.equals(pageKey) ? CUSTOM_PROFILE_FIELD_DEFINITIONS_KEY : pageKey.toLowerCase(Locale.ROOT) + ".field.custom.definitions";
    }

    private List<String> fieldConfigKeys(String pageKey, List<ProfileFieldDefinition> builtInDefinitions) {
        return builtInDefinitions.stream()
                .flatMap(definition -> List.of(definition.visibleConfigKey(), definition.weightConfigKey(), definition.requiredConfigKey(), definition.sortConfigKey()).stream())
                .collect(Collectors.collectingAndThen(Collectors.toCollection(ArrayList::new), keys -> {
                    keys.add(systemOverridesConfigKey(pageKey));
                    keys.add(customDefinitionsConfigKey(pageKey));
                    return List.copyOf(keys);
                }));
    }

    private Map<String, String> loadConfigValuesByKeys(List<String> keys) {
        return repository.findPlatformConfigValues(keys);
    }

    private void upsertConfigValue(
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId,
            String operatorUuid
    ) {
        int updated = repository.upsertPlatformConfig(configKey, configName, configValue, remark, operatorId, operatorUuid);
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Profile config changed, please retry");
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

    private boolean requestedRequired(ProfileFieldSettingItem item, boolean fallback) {
        if (item == null || item.getRequired() == null) {
            return fallback;
        }
        return item.getRequired();
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

    private int resolveRequestedSortNo(ProfileFieldSettingItem item, int fallback) {
        if (item == null || item.getSortNo() == null || item.getSortNo() <= 0) {
            return fallback;
        }
        return item.getSortNo();
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
            case "avatarUrl" -> completed ? "Uploaded" : "Not uploaded";
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
            return "Upload";
        }
        if ("mobile".equals(fieldKey)) {
            return mobileBindAvailable ? "Bind" : "Disabled";
        }
        if ("email".equals(fieldKey)) {
            return emailBindAvailable ? "Bind" : "Disabled";
        }
        return "Complete";
    }

    private String resolveActionHint(String fieldKey, boolean mobileBindAvailable, boolean emailBindAvailable, int scoreContribution) {
        if ("mobile".equals(fieldKey) && !mobileBindAvailable) {
            return "Enable SMS verification before completing this field";
        }
        if ("email".equals(fieldKey) && !emailBindAvailable) {
            return "Enable email verification before completing this field";
        }
        return scoreContribution > 0 ? "Estimated +" + scoreContribution + " points" : null;
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

    private static final class ProfileFieldMetadataOverride {
        private final String fieldKey;
        private final String fieldLabel;
        private final String fieldDescription;
        private final String groupLabel;
        private final String placeholder;

        private ProfileFieldMetadataOverride(
                String fieldKey,
                String fieldLabel,
                String fieldDescription,
                String groupLabel,
                String placeholder
        ) {
            this.fieldKey = fieldKey;
            this.fieldLabel = fieldLabel;
            this.fieldDescription = fieldDescription;
            this.groupLabel = groupLabel;
            this.placeholder = placeholder;
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

        private String groupLabel() {
            return groupLabel;
        }

        private String placeholder() {
            return placeholder;
        }
    }

    private static final class EvaluatedField {
        private final ProfileFieldDefinition definition;
        private final ProfileFieldSettingVO setting;
        private final int weight;

        private EvaluatedField(ProfileFieldDefinition definition, ProfileFieldSettingVO setting, int weight) {
            this.definition = definition;
            this.setting = setting;
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

        private String requiredConfigKey() {
            if (!StringUtils.hasText(visibleConfigKey)) {
                return null;
            }
            return visibleConfigKey.replace(".visible", ".required");
        }

        private String sortConfigKey() {
            if (!StringUtils.hasText(visibleConfigKey)) {
                return null;
            }
            return visibleConfigKey.replace(".visible", ".sort");
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
