package com.lumira.api.system;

public record SystemUserWechatRecipientDTO(
        Long userId,
        String userUuid,
        String username,
        String wechatOpenid
) {
}
