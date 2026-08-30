package com.lumira.saas.modules.system.update.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.system.update.app.PlatformUpdateMaintenanceService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, non-sensitive acknowledgement used by the host updater as a write-barrier handshake. */
@RestController
@RequestMapping("/api/v2/platform/update-maintenance")
public class PlatformUpdateMaintenanceAckController {
    private final PlatformUpdateMaintenanceService maintenanceService;

    public PlatformUpdateMaintenanceAckController(PlatformUpdateMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/ack")
    public ApiResponse<Map<String, Object>> acknowledgement() {
        var state = maintenanceService.currentMode();
        Map<String, Object> acknowledgement = new LinkedHashMap<>();
        acknowledgement.put("taskId", state.taskId());
        acknowledgement.put("mode", state.mode());
        acknowledgement.put("phase", state.phase());
        acknowledgement.put("enforced", !"NORMAL".equals(state.mode()));
        return ApiResponse.success(acknowledgement, TraceContext.getRequestId());
    }
}
