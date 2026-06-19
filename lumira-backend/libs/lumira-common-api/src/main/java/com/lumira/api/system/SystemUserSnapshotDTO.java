package com.lumira.api.system;

public record SystemUserSnapshotDTO(
        Long userId,
        String username,
        String passwordHash,
        String status,
        String mobile,
        String email,
        String nickname,
        String realName,
        String avatarUrl,
        String birthMonth,
        String gender,
        String region,
        String availableTime,
        String idCardNumber,
        String locale
) {
}
