package com.lumira.saas.modules.system.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.system.MaintenanceLoginPolicyDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.system.role.repository.SystemRoleManagementRepository;
import com.lumira.saas.modules.system.settings.repository.SystemPlatformSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Authoritative read/write normalization for the maintenance login role policy. */
@Service
public class MaintenanceLoginPolicyService {

    public static final String GROUP_BRANDING = "BRANDING";
    public static final String MAINTENANCE_MODE_ENABLED_KEY = "branding.maintenance-mode-enabled";
    public static final String ALLOWED_ROLE_IDS_KEY = "branding.maintenance-allowed-role-ids";
    public static final long SEEDED_ADMIN_ROLE_ID = 1001L;

    private final SystemPlatformSettingsRepository settingsRepository;
    private final SystemRoleManagementRepository roleRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public MaintenanceLoginPolicyService(
            SystemPlatformSettingsRepository settingsRepository,
            SystemRoleManagementRepository roleRepository,
            ObjectMapper objectMapper
    ) {
        this.settingsRepository = settingsRepository;
        this.roleRepository = roleRepository;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public MaintenanceLoginPolicyDTO loadEffectivePolicy() {
        Map<String, String> values = settingsRepository.findEffectiveSettingValues(GROUP_BRANDING);
        boolean enabled = Boolean.parseBoolean(values.getOrDefault(MAINTENANCE_MODE_ENABLED_KEY, "false"));
        String rawRoleIds = values.get(ALLOWED_ROLE_IDS_KEY);
        List<Long> roleIds = StringUtils.hasText(rawRoleIds)
                ? parseAllowedRoleIds(rawRoleIds)
                : defaultAdminRoleIds();
        return new MaintenanceLoginPolicyDTO(enabled, activeRoleIds(roleIds));
    }

    public List<Long> resolveRequestedRoleIds(List<Long> requestedRoleIds, boolean enabled) {
        List<Long> candidates = requestedRoleIds == null
                ? loadEffectivePolicy().allowedRoleIds()
                : requestedRoleIds;
        List<Long> activeRoleIds = activeRoleIds(candidates);
        if (enabled && activeRoleIds.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "维护模式开启时至少要允许一个角色登录");
        }
        return activeRoleIds;
    }

    public List<Long> parseAllowedRoleIds(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(rawValue.trim());
            if (root == null || !root.isArray()) {
                return List.of();
            }
            LinkedHashSet<Long> roleIds = new LinkedHashSet<>();
            for (JsonNode item : root) {
                if (item != null && item.canConvertToLong()) {
                    long roleId = item.longValue();
                    if (roleId > 0) {
                        roleIds.add(roleId);
                    }
                }
            }
            return roleIds.stream().sorted().toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public String serializeAllowedRoleIds(List<Long> roleIds) {
        try {
            return objectMapper.writeValueAsString(roleIds == null ? List.of() : roleIds);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "维护模式登录角色配置无法保存");
        }
    }

    public List<Long> defaultAdminRoleIds() {
        if (roleRepository != null) {
            var adminRole = roleRepository.findLatestActiveRoleByCode("ADMIN");
            if (adminRole != null && adminRole.getId() != null && adminRole.getId() > 0) {
                return List.of(adminRole.getId());
            }
        }
        return List.of(SEEDED_ADMIN_ROLE_ID);
    }

    private List<Long> activeRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleIds.stream()
                .filter(roleId -> roleId != null && roleId > 0)
                .distinct()
                .filter(roleId -> roleRepository == null || roleRepository.findActiveRoleById(roleId) != null)
                .sorted()
                .toList();
    }
}
