package com.lumira.saas.modules.system.update.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumira.saas.modules.system.update.app.PlatformUpdateMaintenanceService;
import org.junit.jupiter.api.Test;

class PlatformUpdateMaintenanceAckControllerTest {

    @Test
    void acknowledgementBindsEnforcedModeToPlatformTask() {
        PlatformUpdateMaintenanceService service = mock(PlatformUpdateMaintenanceService.class);
        when(service.currentMode()).thenReturn(new PlatformUpdateMaintenanceService.MaintenanceState(
                42L, "READ_ONLY", "migration", "MIGRATING", false
        ));

        var response = new PlatformUpdateMaintenanceAckController(service).acknowledgement();

        assertThat(response.getData()).containsEntry("taskId", 42L);
        assertThat(response.getData()).containsEntry("mode", "READ_ONLY");
        assertThat(response.getData()).containsEntry("phase", "MIGRATING");
        assertThat(response.getData()).containsEntry("enforced", true);
    }
}
