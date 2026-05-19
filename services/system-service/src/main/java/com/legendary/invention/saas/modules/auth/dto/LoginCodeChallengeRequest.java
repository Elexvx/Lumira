package com.legendary.invention.saas.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginCodeChallengeRequest {

    @NotBlank(message = "登录方式不能为空")
    private String loginType;

    @NotBlank(message = "账号不能为空")
    private String account;

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }
}
