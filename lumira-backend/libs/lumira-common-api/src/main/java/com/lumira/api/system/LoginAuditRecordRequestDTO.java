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
    public LoginAuditRecordRequestDTO(
            Long userId,
            String username,
            String loginType,
            String loginResult,
            String failReason,
            String loginIp,
            String userAgent
    ) {
        this(userId, null, username, loginType, loginResult, failReason, loginIp, userAgent);
    }
}
