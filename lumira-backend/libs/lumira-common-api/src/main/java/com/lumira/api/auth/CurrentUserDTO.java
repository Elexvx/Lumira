package com.lumira.api.auth;

import com.lumira.common.security.data.DataPermissionRule;

import java.util.List;

public record CurrentUserDTO(
        Long userId,
        String userUuid,
        String username,
        String nickname,
        String realName,
        String avatarUrl,
        String mobile,
        String email,
        String birthMonth,
        String gender,
        String region,
        String availableTime,
        String idCardNumber,
        String locale,
        String sessionId,
        String permissionsVersion,
        Integer sessionVersion,
        List<String> permissions,
        List<Long> roleIds,
        Long primaryDeptId,
        List<Long> deptIds,
        List<Long> descendantDeptIds,
        List<DataPermissionRule> dataScopes,
        Boolean requiresPasswordChange,
        String defaultHomePath
) {
    public CurrentUserDTO(
            Long userId,
            String username,
            String nickname,
            String realName,
            String avatarUrl,
            String mobile,
            String email,
            String birthMonth,
            String gender,
            String region,
            String availableTime,
            String idCardNumber,
            String locale,
            String sessionId,
            String permissionsVersion,
            Integer sessionVersion,
            List<String> permissions,
            List<Long> roleIds,
            Long primaryDeptId,
            List<Long> deptIds,
            List<Long> descendantDeptIds,
            List<DataPermissionRule> dataScopes,
            Boolean requiresPasswordChange,
            String defaultHomePath
    ) {
        this(userId, null, username, nickname, realName, avatarUrl, mobile, email, birthMonth, gender, region,
                availableTime, idCardNumber, locale, sessionId, permissionsVersion, sessionVersion, permissions,
                roleIds, primaryDeptId, deptIds, descendantDeptIds, dataScopes, requiresPasswordChange, defaultHomePath);
    }
}
