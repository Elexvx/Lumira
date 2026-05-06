package com.legendary.invention.tenant.controller;

import com.legendary.invention.api.auth.CurrentUserDTO;
import com.legendary.invention.api.client.AuthInternalApi;
import com.legendary.invention.api.tenant.TenantSwitchCheckDTO;
import com.legendary.invention.api.tenant.TenantSwitchRequest;
import com.legendary.invention.api.tenant.TenantSummaryDTO;
import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.security.SecurityContextFacade;
import com.legendary.invention.tenant.domain.TenantDomainService;
import com.legendary.invention.tenant.entity.TenantInfoEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

    @Mock
    private TenantDomainService tenantDomainService;

    @Mock
    private SecurityContextFacade securityContextFacade;

    @Mock
    private AuthInternalApi authInternalApi;

    private TenantController tenantController;

    @BeforeEach
    void setUp() {
        tenantController = new TenantController(tenantDomainService, securityContextFacade, authInternalApi);
    }

    @Test
    void tenant_shouldReturnTenantSummary() {
        TenantSummaryDTO summary = new TenantSummaryDTO(2002L, "tenant-2", "第二租户", "第二租户", "ENABLED", LocalDateTime.now(), LocalDateTime.now());
        when(tenantDomainService.findTenantById(2002L)).thenReturn(Optional.of(tenant(2002L, "tenant-2", "第二租户")));
        when(tenantDomainService.toTenantSummaryDTO(org.mockito.ArgumentMatchers.any())).thenReturn(summary);

        ApiResponse<?> response = tenantController.tenantSummary(2002L);

        assertThat(response.getData()).isNotNull();
        var data = (com.legendary.invention.api.tenant.TenantSummaryDTO) response.getData();
        assertThat(data.tenantId()).isEqualTo(2002L);
        assertThat(data.tenantCode()).isEqualTo("tenant-2");
        assertThat(data.tenantName()).isEqualTo("第二租户");
    }

    @Test
    void switchCheck_shouldReturnValidationResult() {
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        TenantSwitchCheckDTO check = new TenantSwitchCheckDTO(
                2002L,
                new TenantSummaryDTO(2002L, "tenant-2", "第二租户", "第二租户", "ENABLED", LocalDateTime.now(), LocalDateTime.now()),
                true,
                "可以切换到该租户"
        );
        when(tenantDomainService.validateTenantSwitch(100L, 2002L)).thenReturn(check);

        ApiResponse<?> response = tenantController.switchCheck(2002L);

        assertThat(response.getData()).isEqualTo(check);
    }

    @Test
    void switchTenant_shouldDelegateToAuthServiceWhenTenantIsBound() {
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(tenantDomainService.validateTenantSwitch(100L, 2002L)).thenReturn(new TenantSwitchCheckDTO(
                2002L,
                new TenantSummaryDTO(2002L, "tenant-2", "第二租户", "第二租户", "ENABLED", LocalDateTime.now(), LocalDateTime.now()),
                true,
                "可以切换到该租户"
        ));
        when(authInternalApi.switchSessionTenant("session-1", 2002L)).thenReturn(true);

        ApiResponse<Boolean> response = tenantController.switchTenant(new TenantSwitchRequest(2002L));

        assertThat(response.getData()).isTrue();
        verify(authInternalApi).switchSessionTenant("session-1", 2002L);
    }

    @Test
    void switchTenant_shouldShortCircuitWhenTenantIsAlreadyCurrent() {
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(tenantDomainService.validateTenantSwitch(100L, 1001L)).thenReturn(new TenantSwitchCheckDTO(
                1001L,
                new TenantSummaryDTO(1001L, "platform", "平台租户", "平台租户", "ENABLED", LocalDateTime.now(), LocalDateTime.now()),
                true,
                "可以切换到该租户"
        ));

        ApiResponse<Boolean> response = tenantController.switchTenant(new TenantSwitchRequest(1001L));

        assertThat(response.getData()).isTrue();
        verify(authInternalApi, never()).switchSessionTenant("session-1", 1001L);
    }

    @Test
    void switchTenant_shouldRejectUnboundTenantWithoutTouchingAuthSession() {
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(tenantDomainService.validateTenantSwitch(100L, 2002L)).thenReturn(new TenantSwitchCheckDTO(2002L, null, false, "当前账号未绑定该租户"));

        assertThatThrownBy(() -> tenantController.switchTenant(new TenantSwitchRequest(2002L)))
                .isInstanceOf(BizException.class);

        verify(authInternalApi, never()).switchSessionTenant("session-1", 2002L);
    }

    private CurrentUser currentUser() {
        return new CurrentUser(100L, "alice", 1001L, "session-1", 3, true, Set.of("tenant:view"));
    }

    private TenantInfoEntity tenant(Long id, String code, String name) {
        TenantInfoEntity tenant = new TenantInfoEntity();
        tenant.setId(id);
        tenant.setTenantCode(code);
        tenant.setTenantName(name);
        tenant.setTenantShortName(name);
        tenant.setStatus("ENABLED");
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenant.setDeleted(0);
        return tenant;
    }
}
