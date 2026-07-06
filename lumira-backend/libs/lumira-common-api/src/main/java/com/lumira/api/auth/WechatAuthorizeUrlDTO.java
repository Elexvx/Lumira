package com.lumira.api.auth;

public record WechatAuthorizeUrlDTO(
        String authorizeUrl,
        String state,
        String appId,
        String scope,
        String redirectUri,
        String encodedRedirectUri
) {
    public WechatAuthorizeUrlDTO(String authorizeUrl, String state) {
        this(authorizeUrl, state, null, null, null, null);
    }
}
