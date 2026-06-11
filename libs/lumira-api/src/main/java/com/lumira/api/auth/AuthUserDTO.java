package com.lumira.api.auth;

import java.util.List;

public record AuthUserDTO(
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
        List<String> permissions
) {
}
