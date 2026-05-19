package com.legendary.invention.saas.modules.auth.vo;

public class WechatAuthorizeUrlVO {

    private String authorizeUrl;
    private String state;

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }

    public void setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
