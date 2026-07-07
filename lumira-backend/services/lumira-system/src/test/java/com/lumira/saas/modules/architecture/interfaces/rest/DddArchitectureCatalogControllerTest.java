package com.lumira.saas.modules.architecture.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DddArchitectureCatalogControllerTest {

    @Test
    void contexts_shouldExposeV2DddContractForAllBoundedContexts() {
        DddArchitectureCatalogController controller = new DddArchitectureCatalogController(
                securityContext(Set.of("system:config:view")),
                new PermissionGuard()
        );

        var response = controller.contexts();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData().architecture()).isEqualTo("ddd-modular-monolith");
        assertThat(response.getData().contexts())
                .hasSize(10)
                .extracting(DddArchitectureCatalogController.BoundedContextResponse::name)
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "IAM",
                        "Auth",
                        "Platform",
                        "Message",
                        "File",
                        "Plugin",
                        "Localization",
                        "Payment",
                        "AI",
                        "Job"
                ));
        assertThat(response.getData().contexts())
                .allSatisfy(context -> {
                    assertThat(context.ownerModule()).isNotBlank();
                    assertThat(context.primaryModels()).isNotBlank();
                    assertThat(context.readModelCacheKey()).isEqualTo("context:scope:version");
                });
        assertThat(response.getData().invariants())
                .contains(
                        "Commands write only owner aggregates and publish domain events.",
                        "Cross-context access must use contracts, events, projections, or cache snapshots."
                );
    }

    @Test
    void contexts_shouldRequireConfigViewPermission() {
        DddArchitectureCatalogController controller = new DddArchitectureCatalogController(
                securityContext(Set.of("system:monitor:service:view")),
                new PermissionGuard()
        );

        assertThatThrownBy(controller::contexts)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("system:config:view");
    }

    @Test
    void contexts_shouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        PermissionGuard permissionGuard = Mockito.mock(PermissionGuard.class);
        DddArchitectureCatalogController controller = new DddArchitectureCatalogController(
                securityContext(Set.of("system:config:view")),
                permissionGuard,
                null
        );

        assertThatThrownBy(controller::contexts)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Trusted user resolver is unavailable");

        verify(permissionGuard, never()).requirePermission(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void contexts_shouldRejectWhenTrustedPermissionSnapshotIsUnavailableBeforePermissionCheck() {
        PermissionGuard permissionGuard = Mockito.mock(PermissionGuard.class);
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(new SystemUserSnapshotDTO(1001L, "user-uuid-1001", "architect-live", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null));
        when(systemInternalApi.permissionSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        DddArchitectureCatalogController controller = new DddArchitectureCatalogController(
                securityContext(Set.of("system:config:view")),
                permissionGuard,
                systemInternalApi
        );

        assertThatThrownBy(controller::contexts)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Trusted user permission snapshot is unavailable");

        verifyNoInteractions(permissionGuard);
    }

    @Test
    void contexts_shouldRejectBlankLiveUsernameBeforePermissionCheck() {
        PermissionGuard permissionGuard = Mockito.mock(PermissionGuard.class);
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(new SystemUserSnapshotDTO(1001L, "user-uuid-1001", " ", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null));
        when(systemInternalApi.permissionSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new com.lumira.api.system.PermissionSnapshotDTO("permissions-2", java.util.List.of("system:config:view"), java.util.List.of(1L), 1L, java.util.List.of(1L), java.util.List.of(1L), java.util.List.of(), "/architecture"));
        DddArchitectureCatalogController controller = new DddArchitectureCatalogController(
                securityContext(Set.of("system:config:view")),
                permissionGuard,
                systemInternalApi
        );

        assertThatThrownBy(controller::contexts)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Trusted user username is unavailable");

        verifyNoInteractions(permissionGuard);
    }

    @Test
    void contexts_shouldUseSimulatedRolePermissionSnapshotBeforePermissionCheck() {
        PermissionGuard permissionGuard = Mockito.mock(PermissionGuard.class);
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(new SystemUserSnapshotDTO(1001L, "user-uuid-1001", "architect-live", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null));
        when(systemInternalApi.simulatedRolePermissionSnapshot(1001L, "user-uuid-1001", 9L))
                .thenReturn(permissionSnapshot("system:config:view"));
        DddArchitectureCatalogController controller = new DddArchitectureCatalogController(
                securityContext(Set.of("system:config:view"), 9L),
                permissionGuard,
                systemInternalApi
        );

        controller.contexts();

        verify(systemInternalApi).simulatedRolePermissionSnapshot(1001L, "user-uuid-1001", 9L);
        verify(systemInternalApi, never()).permissionSnapshot(1001L, "user-uuid-1001");
        verify(permissionGuard).requirePermission(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("system:config:view"));
    }

    @Test
    void contexts_shouldNormalizeInvalidSimulatedRoleIdBeforePermissionCheck() {
        PermissionGuard permissionGuard = Mockito.mock(PermissionGuard.class);
        SecurityContextFacade securityContextFacade = Mockito.mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(
                1001L,
                "architect",
                null,
                "session-1",
                1,
                true,
                Set.of("system:config:view")
        );
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setSimulatedRoleId(0L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(new SystemUserSnapshotDTO(1001L, "user-uuid-1001", "architect-live", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null));
        when(systemInternalApi.permissionSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(permissionSnapshot("system:config:view"));
        DddArchitectureCatalogController controller = new DddArchitectureCatalogController(
                securityContextFacade,
                permissionGuard,
                systemInternalApi
        );

        controller.contexts();

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(systemInternalApi).permissionSnapshot(1001L, "user-uuid-1001");
        verify(systemInternalApi, never()).simulatedRolePermissionSnapshot(1001L, "user-uuid-1001", 0L);
        verify(permissionGuard).requirePermission(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("system:config:view"));
    }

    private SecurityContextFacade securityContext(Set<String> permissions) {
        return securityContext(permissions, null);
    }

    private SecurityContextFacade securityContext(Set<String> permissions, Long simulatedRoleId) {
        SecurityContextFacade securityContextFacade = Mockito.mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(
                1001L,
                "architect",
                null,
                "session-1",
                1,
                true,
                permissions
        );
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setSimulatedRoleId(simulatedRoleId);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        return securityContextFacade;
    }

    private PermissionSnapshotDTO permissionSnapshot(String permission) {
        return new PermissionSnapshotDTO("permissions-2", java.util.List.of(permission), java.util.List.of(9L), 1L, java.util.List.of(1L), java.util.List.of(1L), java.util.List.of(), "/architecture");
    }
}
