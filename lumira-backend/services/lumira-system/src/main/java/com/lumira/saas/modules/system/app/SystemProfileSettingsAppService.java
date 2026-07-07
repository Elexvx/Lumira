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
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.dao.EmptyResultDataAccessException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemProfileSettingsAppService {

    public static final String PROFILE_PAGE_KEY = "PROFILE";
    public static final String TEAM_MEMBER_PAGE_KEY = "TEAM_MEMBER";
    private static final Set<String> SUPPORTED_PAGE_KEYS = Set.of(PROFILE_PAGE_KEY, TEAM_MEMBER_PAGE_KEY);
    private static final String PROFILE_SETTINGS_CACHE_KEY_PREFIX = "global-field-settings:";
    private static final long PROFILE_SETTINGS_CACHE_TTL_MS = 30_000L;
    private static final int PROFILE_SETTINGS_CACHE_MAX_ENTRIES = 2048;
    private static final Integer PROFILE_SCORE_MAX = 100;
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String PROFILE_FIELD_GROUP_BASIC_KEY = "basic";
    private static final String PROFILE_FIELD_GROUP_CONTACT_KEY = "contact";
    private static final String PROFILE_FIELD_GROUP_IDENTITY_KEY = "identity";
    private static final String PROFILE_FIELD_GROUP_CUSTOM_KEY = "custom";
    private static final String PROFILE_FIELD_GROUP_CUSTOM_LABEL = "Custom profile";
    private static final String SYSTEM_PROFILE_FIELD_OVERRIDES_KEY = "profile.field.system.overrides";
    private static final String CUSTOM_PROFILE_FIELD_DEFINITIONS_KEY = "profile.field.custom.definitions";
    private static final Set<String> SUPPORTED_CUSTOM_FIELD_TYPES = Set.of("TEXT", "NUMBER", "DATE", "SELECT", "TEXTAREA");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<ProfileFieldDefinition> PROFILE_FIELD_DEFINITIONS = List.of(
            new ProfileFieldDefinition("avatarUrl", "Avatar", "Controls whether profile avatar upload and preview are shown", PROFILE_FIELD_GROUP_BASIC_KEY, "Basic profile", "profile.field.avatar.visible", "profile.field.avatar.weight", true, 10, "IMAGE", false, null, 10, false),
            new ProfileFieldDefinition("realName", "Real name", "Controls whether the real-name profile field is shown", PROFILE_FIELD_GROUP_BASIC_KEY, "Basic profile", "profile.field.real-name.visible", "profile.field.real-name.weight", true, 15, "TEXT", false, "Enter real name", 20, false),
            new ProfileFieldDefinition("mobile", "Mobile", "Controls whether the mobile profile field is shown", PROFILE_FIELD_GROUP_CONTACT_KEY, "Contact", "profile.field.mobile.visible", "profile.field.mobile.weight", true, 15, "MOBILE", false, "Enter mobile number", 30, false),
            new ProfileFieldDefinition("email", "Email", "Controls whether the email profile field is shown", PROFILE_FIELD_GROUP_CONTACT_KEY, "Contact", "profile.field.email.visible", "profile.field.email.weight", true, 15, "EMAIL", false, "Enter email address", 40, false),
            new ProfileFieldDefinition("birthMonth", "Birth month", "Controls whether the birth-month profile field is shown", PROFILE_FIELD_GROUP_BASIC_KEY, "Basic profile", "profile.field.birth-month.visible", "profile.field.birth-month.weight", true, 10, "MONTH", false, "Select birth month", 50, false),
            new ProfileFieldDefinition("gender", "Gender", "Controls whether the gender profile field is shown", PROFILE_FIELD_GROUP_BASIC_KEY, "Basic profile", "profile.field.gender.visible", "profile.field.gender.weight", true, 10, "SELECT", false, "Select gender", 60, false),
            new ProfileFieldDefinition("region", "Region", "Controls whether the region profile field is shown", PROFILE_FIELD_GROUP_BASIC_KEY, "Basic profile", "profile.field.region.visible", "profile.field.region.weight", true, 10, "TEXT", false, "Enter region", 70, false),
            new ProfileFieldDefinition("idCardNumber", "ID card number", "Controls whether the ID-card profile field is shown", PROFILE_FIELD_GROUP_IDENTITY_KEY, "Identity", "profile.field.id-card-number.visible", "profile.field.id-card-number.weight", true, 5, "ID_CARD", false, "Enter ID card number", 80, false)
    );
    private static final List<ProfileFieldDefinition> TEAM_MEMBER_FIELD_DEFINITIONS = List.of(
            new ProfileFieldDefinition("memberName", "Member name", "Team member name", "teamMember", "Team member", "team.member.field.member-name.visible", "team.member.field.member-name.weight", true, 10, "TEXT", true, "Enter member name", 10, false),
            new ProfileFieldDefinition("employeeNo", "Employee number", "Team member employee or student number", "teamMember", "Team member", "team.member.field.employee-no.visible", "team.member.field.employee-no.weight", true, 5, "TEXT", false, "Enter employee or student number", 20, false),
            new ProfileFieldDefinition("departmentName", "Department", "Team member department", "teamMember", "Team member", "team.member.field.department-name.visible", "team.member.field.department-name.weight", true, 5, "TEXT", false, "Enter department", 30, false),
            new ProfileFieldDefinition("role", "Role", "Team member role", "teamMember", "Team member", "team.member.field.role.visible", "team.member.field.role.weight", true, 5, "SELECT", false, "Select role", 40, false),
            new ProfileFieldDefinition("remark", "Remark", "Team member remark", "teamMember", "Team member", "team.member.field.remark.visible", "team.member.field.remark.weight", true, 5, "TEXTAREA", false, "Enter remark", 50, false)
    );

    private final MyBatisQueryOperations jdbcTemplate;
    private final OperationAuditService operationAuditService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;
    private final Cache<String, List<ProfileFieldSettingVO>> profileFieldSettingsCache;
    private final Cache<String, CompletableFuture<List<ProfileFieldSettingVO>>> profileFieldSettingsLoadInFlight;

    public SystemProfileSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, operationAuditService, permissionSnapshotService, null, null, false);
    }

    @Autowired
    public SystemProfileSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, operationAuditService, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private SystemProfileSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationAuditService = operationAuditService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
        this.profileFieldSettingsCache = CacheBuilder.newBuilder()
                .maximumSize(PROFILE_SETTINGS_CACHE_MAX_ENTRIES)
                .expireAfterWrite(PROFILE_SETTINGS_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.profileFieldSettingsLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(PROFILE_SETTINGS_CACHE_MAX_ENTRIES)
                .expireAfterWrite(PROFILE_SETTINGS_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    public SystemProfileSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, operationAuditService, permissionSnapshotService, null, sessionAuthenticationService, false);
    }

    public SystemProfileSettingsAppService(MyBatisQueryOperations jdbcTemplate, OperationAuditService operationAuditService) {
        this(jdbcTemplate, operationAuditService, null, null, null, false);
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
        invalidateProfileFieldSettingsCache(normalizedPageKey);
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
        List<ProfileFieldSettingVO> cached = profileFieldSettingsCache.getIfPresent(cacheKey(pageKey));
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        return loadProfileFieldSettingsWithSingleFlight(pageKey);
    }

    private List<ProfileFieldSettingVO> loadProfileFieldSettingsWithSingleFlight(String pageKey) {
        try {
            CompletableFuture<List<ProfileFieldSettingVO>> future = profileFieldSettingsLoadInFlight.get(
                    cacheKey(pageKey),
                    () -> CompletableFuture.completedFuture(loadProfileFieldSettingsFresh(pageKey))
            );
            List<ProfileFieldSettingVO> settings = future.join();
            profileFieldSettingsLoadInFlight.invalidate(cacheKey(pageKey));
            return settings;
        } catch (ExecutionException ex) {
            profileFieldSettingsLoadInFlight.invalidate(cacheKey(pageKey));
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load profile field settings", cause);
        } catch (RuntimeException ex) {
            profileFieldSettingsLoadInFlight.invalidate(cacheKey(pageKey));
            throw ex;
        }
    }

    private List<ProfileFieldSettingVO> loadProfileFieldSettingsFresh(String pageKey) {
        List<ProfileFieldSettingVO> cached = profileFieldSettingsCache.getIfPresent(cacheKey(pageKey));
        if (cached != null) {
            return new ArrayList<>(cached);
        }
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
        profileFieldSettingsCache.put(cacheKey(pageKey), new ArrayList<>(settings));
        return settings;
    }

    private void invalidateProfileFieldSettingsCache(String pageKey) {
        profileFieldSettingsCache.invalidate(cacheKey(pageKey));
        profileFieldSettingsLoadInFlight.invalidate(cacheKey(pageKey));
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

    private String normalizePageKey(String pageKey) {
        String normalized = StringUtils.hasText(pageKey) ? pageKey.trim().toUpperCase() : PROFILE_PAGE_KEY;
        return SUPPORTED_PAGE_KEYS.contains(normalized) ? normalized : PROFILE_PAGE_KEY;
    }

    private List<ProfileFieldDefinition> builtInDefinitions(String pageKey) {
        return TEAM_MEMBER_PAGE_KEY.equals(pageKey) ? TEAM_MEMBER_FIELD_DEFINITIONS : PROFILE_FIELD_DEFINITIONS;
    }

    private String cacheKey(String pageKey) {
        return PROFILE_SETTINGS_CACHE_KEY_PREFIX + pageKey;
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
        String placeholders = keys.stream().map(item -> "?").collect(Collectors.joining(", "));
        String sql = """
                select config_key as configKey, config_value as configValue
                from sys_config
                where deleted = 0
                  and config_scope = 'PLATFORM'
                  and config_key in (%s)
                order by id desc
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(keys);
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
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId,
            String operatorUuid
    ) {
        Long existingId = queryConfigId(configKey);
        if (existingId == null) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into sys_config (
                                config_key, config_name, config_value, config_scope, is_system, remark,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, 'PLATFORM', 0, ?, ?, ?, ?, 0)
                            """,
                    configKey,
                    configName,
                    configValue,
                    remark,
                    operatorId,
                    operatorUuid,
                    operatorId,
                    operatorUuid
            );
            if (inserted != 1) {
                throw new BizException(ErrorCode.BIZ_ERROR, "Profile config changed, please retry");
            }
            return;
        }
        int updated = jdbcTemplate.update(
                """
                        update sys_config
                        set config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and config_key = ?
                          and config_scope = 'PLATFORM'
                          and is_system = 0
                          and deleted = 0
                        """,
                configName,
                configValue,
                remark,
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                existingId,
                configKey
        );
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Profile config changed, please retry");
        }
    }

    private Long queryConfigId(String configKey) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id
                            from sys_config
                            where config_key = ?
                              and config_scope = 'PLATFORM'
                              and is_system = 0
                              and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    configKey
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
