package com.lumira.api.system;

public record WechatLoginUserRequestDTO(
        String openid,
        String unionid,
        String scope,
        String nickname,
        String avatarUrl,
        String country,
        String province,
        String city,
        Integer sex
) {
    public WechatLoginUserRequestDTO(String openid, String unionid, String scope) {
        this(openid, unionid, scope, null, null, null, null, null, null);
    }
}
