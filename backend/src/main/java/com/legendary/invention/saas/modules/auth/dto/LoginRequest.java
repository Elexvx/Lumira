package com.legendary.invention.saas.modules.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    private String account;
    private String username;
    private String mobile;

    @NotBlank(message = "密码不能为空")
    private String password;
    private String captchaId;
    private String captchaCode;
    private String captchaProof;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public void setCaptchaCode(String captchaCode) {
        this.captchaCode = captchaCode;
    }

    public String getCaptchaProof() {
        return captchaProof;
    }

    public void setCaptchaProof(String captchaProof) {
        this.captchaProof = captchaProof;
    }

    @AssertTrue(message = "账号、用户名或手机号至少填写一项")
    public boolean isAccountProvided() {
        return (account != null && !account.isBlank())
                || (username != null && !username.isBlank())
                || (mobile != null && !mobile.isBlank());
    }

    public String account() {
        if (account != null && !account.isBlank()) {
            return account.trim();
        }
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        return mobile == null ? "" : mobile.trim();
    }
}
