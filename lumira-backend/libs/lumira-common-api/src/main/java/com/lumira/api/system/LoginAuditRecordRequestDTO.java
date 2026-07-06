package com.lumira.api.system;

public record LoginAuditRecordRequestDTO(
        Long userId,
        String userUuid,
        String username,
        String loginType,
        String loginResult,
        String failReason,
        String loginIp,
        String userAgent
) {
}
