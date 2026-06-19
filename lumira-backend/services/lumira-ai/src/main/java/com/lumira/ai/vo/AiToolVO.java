package com.lumira.ai.vo;

import java.util.Map;

public record AiToolVO(
        String toolCode,
        String toolName,
        String category,
        String description,
        String riskLevel,
        Boolean readOnly,
        Boolean needConfirm,
        String requiredPermission,
        Map<String, Object> inputSchema
) {
}
