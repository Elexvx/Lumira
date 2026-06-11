package com.lumira.saas.modules.system.captcha.vo;

public class CaptchaVerifyVO {

    private String captchaId;
    private String captchaProof;
    private Integer expiresInSeconds;

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public String getCaptchaProof() { return captchaProof; }
    public void setCaptchaProof(String captchaProof) { this.captchaProof = captchaProof; }
    public Integer getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Integer expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
}
