package com.legendary.invention.saas.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class WechatLoginRequest {

    @NotBlank(message = "微信授权 code 不能为空")
    private String code;

    @NotBlank(message = "微信授权 state 不能为空")
    private String state;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
