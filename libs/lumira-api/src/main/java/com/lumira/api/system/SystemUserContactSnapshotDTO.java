package com.lumira.api.system;

public record SystemUserContactSnapshotDTO(
        Long userId,
        String username,
        String email,
        String wechatOpenid
) {
}
