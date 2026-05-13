package com.legendary.invention.saas.modules.system.verification.vo;

public class LoginCapabilitiesVO {

    private Boolean passwordLoginAvailable;
    private Boolean smsLoginAvailable;
    private Boolean emailLoginAvailable;
    private Boolean wechatLoginAvailable;

    public Boolean getPasswordLoginAvailable() {
        return passwordLoginAvailable;
    }

    public void setPasswordLoginAvailable(Boolean passwordLoginAvailable) {
        this.passwordLoginAvailable = passwordLoginAvailable;
    }

    public Boolean getSmsLoginAvailable() {
        return smsLoginAvailable;
    }

    public void setSmsLoginAvailable(Boolean smsLoginAvailable) {
        this.smsLoginAvailable = smsLoginAvailable;
    }

    public Boolean getEmailLoginAvailable() {
        return emailLoginAvailable;
    }

    public void setEmailLoginAvailable(Boolean emailLoginAvailable) {
        this.emailLoginAvailable = emailLoginAvailable;
    }

    public Boolean getWechatLoginAvailable() {
        return wechatLoginAvailable;
    }

    public void setWechatLoginAvailable(Boolean wechatLoginAvailable) {
        this.wechatLoginAvailable = wechatLoginAvailable;
    }
}
