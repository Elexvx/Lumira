package com.lumira.saas.modules.system.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.system.role.repository.SystemRoleManagementRepository;
import com.lumira.saas.modules.system.settings.repository.SystemPlatformSettingsRepository;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaintenanceLoginPolicyServiceTest {

    private final SystemPlatformSettingsRepository settingsRepository = mock(SystemPlatformSettingsRepository.class);
    private final SystemRoleManagementRepository roleRepository = mock(SystemRoleManagementRepository.class);
    private final MaintenanceLoginPolicyService service = new MaintenanceLoginPolicyService(
            settingsRepository,
            roleRepository,
            new ObjectMapper()
    );

    @Test
    void parseAllowedRoleIdsShouldDropInvalidValuesAndDeduplicate() {
        assertThat(service.parseAllowedRoleIds("[3002, 1001, 3002, -1, 0, 1001]"))
                .containsExactly(1001L, 3002L);
        assertThat(service.parseAllowedRoleIds("{\"roleId\":1001}"))
                .isEmpty();
        assertThat(service.parseAllowedRoleIds("not-json"))
                .isEmpty();
    }

    @Test
    void loadEffectivePolicyShouldFilterDeletedRolesAndKeepEnabledState() {
        when(settingsRepository.findEffectiveSettingValues("BRANDING")).thenReturn(Map.of(
                MaintenanceLoginPolicyService.MAINTENANCE_MODE_ENABLED_KEY, "true",
                MaintenanceLoginPolicyService.ALLOWED_ROLE_IDS_KEY, "[3002, 1001, 3002]"
        ));
        when(roleRepository.findActiveRoleById(1001L)).thenReturn(activeRole(1001L));
        when(roleRepository.findActiveRoleById(3002L)).thenReturn(activeRole(3002L));

        assertThat(service.loadEffectivePolicy().enabled()).isTrue();
        assertThat(service.loadEffectivePolicy().allowedRoleIds())
                .containsExactly(1001L, 3002L);
    }

    @Test
    void missingStoredPolicyShouldFallBackToTheActiveAdminRole() {
        when(settingsRepository.findEffectiveSettingValues("BRANDING")).thenReturn(Map.of(
                MaintenanceLoginPolicyService.MAINTENANCE_MODE_ENABLED_KEY, "true"
        ));
        when(roleRepository.findLatestActiveRoleByCode("ADMIN")).thenReturn(activeRole(2001L));
        when(roleRepository.findActiveRoleById(2001L)).thenReturn(activeRole(2001L));

        assertThat(service.loadEffectivePolicy().allowedRoleIds()).containsExactly(2001L);
    }

    @Test
    void enabledPolicyShouldRejectAnEmptyOrInvalidRequestedRoleList() {
        assertThatThrownBy(() -> service.resolveRequestedRoleIds(List.of(9999L), true))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void requestedRoleIdsShouldBeValidatedAgainstActiveRolesAndDeduplicated() {
        when(roleRepository.findActiveRoleById(3002L)).thenReturn(activeRole(3002L));

        assertThat(service.resolveRequestedRoleIds(List.of(3002L, 3002L, -1L), true))
                .containsExactly(3002L);
    }

    private static SystemVO.RoleVO activeRole(Long roleId) {
        SystemVO.RoleVO role = new SystemVO.RoleVO();
        role.setId(roleId);
        role.setRoleCode(roleId == 1001L ? "ADMIN" : "ROLE_" + roleId);
        role.setRoleName(role.getRoleCode());
        role.setRoleType("BUILT_IN");
        return role;
    }
}
