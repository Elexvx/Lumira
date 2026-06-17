package com.lumira.ai.vo;

import java.time.LocalDateTime;
import java.util.Map;

public record AiToolExecuteResultVO(
        String toolCode,
        String resultStatus,
        String message,
        Map<String, Object> data,
        LocalDateTime executedAt
) {
}
