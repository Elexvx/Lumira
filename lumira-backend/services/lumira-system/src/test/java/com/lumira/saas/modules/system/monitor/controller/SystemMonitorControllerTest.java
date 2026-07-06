package com.lumira.saas.modules.system.monitor.controller;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.monitor.app.SystemMonitorAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemMonitorControllerTest {

    @Test
    void serviceMonitorShouldRejectWhenLiveSnapshotRevokesServiceViewPermissionBeforeDelegating() {
        SystemMonitorAppService systemMonitorAppService = mock(SystemMonitorAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser("system:monitor:service:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:monitor:redis:view")));
        SystemMonitorController controller = new SystemMonitorController(
                systemMonitorAppService,
                securityContextFacade,
                new PermissionGuard(),
                objectProvider(null),
                permissionSnapshotService
        );

        assertThatThrownBy(controller::serviceMonitor)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(systemMonitorAppService, never()).getServiceMonitor(any());
    }

    @Test
    void serviceMonitorShouldRejectRevokedSessionTicketBeforeDelegating() {
        SystemMonitorAppService systemMonitorAppService = mock(SystemMonitorAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser currentUser = currentUser("system:monitor:service:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        SystemMonitorController controller = new SystemMonitorController(
                systemMonitorAppService,
                securityContextFacade,
                new PermissionGuard(),
                objectProvider(null),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(controller::serviceMonitor)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(systemMonitorAppService, never()).getServiceMonitor(any());
    }

    private static CurrentUser currentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private static <T> ObjectProvider<T> objectProvider(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public T getObject() { return value; }
            @Override public java.util.Iterator<T> iterator() { return value == null ? java.util.List.<T>of().iterator() : java.util.List.of(value).iterator(); }
            @Override public Stream<T> stream() { return value == null ? Stream.empty() : Stream.of(value); }
            @Override public Stream<T> orderedStream() { return stream(); }
        };
    }
}
