package com.lumira.saas.modules.system.config.controller;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.system.config.app.SystemConfigVersioningService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemConfigVersionControllerTest {

    @Test
    void rollbackRequiresUpdatePermissionAndExpectedCurrentVersion() {
        SystemConfigVersioningService versioningService = mock(SystemConfigVersioningService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        OperationAuditService operationAuditService = mock(OperationAuditService.class);
        CurrentUser currentUser = currentUser(Set.of("system:config:update"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemConfigVersionController controller = new SystemConfigVersionController(
                versioningService,
                securityContextFacade,
                new PermissionGuard(),
                operationAuditService
        );

        assertThatThrownBy(() -> controller.rollback(
                3L,
                "BRANDING",
                "PLATFORM",
                new SystemConfigVersionController.RollbackRequest()
        )).isInstanceOfSatisfying(BizException.class, error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(versioningService, never()).rollback(any(), anyLong(), anyLong());
        verify(operationAuditService, never()).log(
                anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void rollbackAuditsSuccessfulVersionedRestoreWithoutExposingSecretValues() {
        SystemConfigVersioningService versioningService = mock(SystemConfigVersioningService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        OperationAuditService operationAuditService = mock(OperationAuditService.class);
        CurrentUser currentUser = currentUser(Set.of("system:config:update"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemConfigVersioningService.VersionSummary summary = new SystemConfigVersioningService.VersionSummary(
                11L,
                "SMTP",
                "PLATFORM",
                3L,
                SystemConfigVersioningService.CHANGE_ROLLBACK,
                "restore known-good smtp settings",
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                2L,
                1L,
                LocalDateTime.now()
        );
        SystemConfigVersioningService.VersionDetail detail = new SystemConfigVersioningService.VersionDetail(
                summary,
                List.of(new SystemConfigVersioningService.DiffItem(
                        "smtp.password", SystemConfigVersioningService.SENSITIVITY_SECRET, "******", "******", "UPDATE"
                ))
        );
        when(versioningService.rollback(any(), eq(3L), eq(2L))).thenReturn(detail);
        SystemConfigVersionController controller = new SystemConfigVersionController(
                versioningService,
                securityContextFacade,
                new PermissionGuard(),
                operationAuditService
        );
        SystemConfigVersionController.RollbackRequest request = new SystemConfigVersionController.RollbackRequest();
        request.setExpectedConfigVersion(2L);
        request.setChangeReason("restore known-good smtp settings");

        assertThat(controller.rollback(3L, "SMTP", "PLATFORM", request).getData())
                .isSameAs(detail)
                .satisfies(result -> assertThat(result.diff().get(0).afterValue()).isEqualTo("******"));

        verify(operationAuditService).log(
                eq(currentUser.getUserId()),
                eq(currentUser.getUserUuid()),
                eq(currentUser.getUsername()),
                eq("config-version"),
                eq("rollback"),
                eq("ROLLBACK"),
                eq("SUCCESS"),
                anyString()
        );
    }

    private static CurrentUser currentUser(Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(permissions);
        return currentUser;
    }
}
