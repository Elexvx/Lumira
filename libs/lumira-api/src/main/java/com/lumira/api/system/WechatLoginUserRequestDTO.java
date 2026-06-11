package com.lumira.api.system;

public record WechatLoginUserRequestDTO(String openid, String unionid, String scope) {
}
