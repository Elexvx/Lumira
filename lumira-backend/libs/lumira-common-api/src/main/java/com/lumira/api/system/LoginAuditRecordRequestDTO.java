package com.lumira.api.system;

public record LoginAuditRecordRequestDTO(
        Long userId,
        String username,
        String loginType,
        String loginResult,
        String failReason,
        String loginIp,
        String userAgent
) {
}
