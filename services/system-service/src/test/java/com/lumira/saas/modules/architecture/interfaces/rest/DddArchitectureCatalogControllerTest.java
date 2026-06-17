package com.lumira.saas.modules.architecture.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DddArchitectureCatalogControllerTest {

    @Test
    void contexts_shouldExposeV2DddContractForAllBoundedContexts() {
        DddArchitectureCatalogController controller = new DddArchitectureCatalogController();

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
                    assertThat(context.readModelCacheKey()).isEqualTo("tenantId:version:scope");
                });
        assertThat(response.getData().invariants())
                .contains(
                        "Commands write only owner aggregates and publish domain events.",
                        "Cross-context access must use contracts, events, projections, or cache snapshots."
                );
    }
}
