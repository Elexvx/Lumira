package com.legendary.invention.api.auth;

import com.legendary.invention.api.tenant.TenantSummaryDTO;

import java.util.List;

public record CurrentUserDTO(
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
        TenantSummaryDTO currentTenant,
        String sessionId,
        String permissionsVersion,
        Integer sessionVersion,
        List<String> permissions
) {
}
