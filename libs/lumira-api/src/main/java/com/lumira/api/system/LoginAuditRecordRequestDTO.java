package com.lumira.api.system;

public record LoginAuditRecordRequestDTO(
        Long userId,
        Long tenantId,
        String username,
        String loginType,
        String loginResult,
        String failReason,
        String loginIp,
        String userAgent
) {
}
