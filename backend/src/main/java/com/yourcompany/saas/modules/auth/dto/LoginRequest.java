package com.yourcompany.saas.modules.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    private String username;
    private String mobile;

    @NotBlank(message = "密码不能为空")
    private String password;

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

    @AssertTrue(message = "用户名和手机号至少填写一项")
    public boolean isAccountProvided() {
        return (username != null && !username.isBlank()) || (mobile != null && !mobile.isBlank());
    }

    public String account() {
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        return mobile == null ? "" : mobile.trim();
    }
}
