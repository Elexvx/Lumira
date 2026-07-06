package com.lumira.saas.modules.architecture.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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

    private SecurityContextFacade securityContext(Set<String> permissions) {
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
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        return securityContextFacade;
    }
}
