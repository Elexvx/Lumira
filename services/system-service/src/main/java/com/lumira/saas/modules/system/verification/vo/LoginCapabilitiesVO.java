package com.lumira.saas.modules.system.verification.vo;

import java.util.List;

public class LoginCapabilitiesVO {

    private Boolean passwordLoginAvailable;
    private Boolean smsLoginAvailable;
    private Boolean emailLoginAvailable;
    private Boolean wechatLoginAvailable;
    private Boolean passkeyLoginAvailable;
    private Boolean passkeyPasswordlessAvailable;
    private List<String> loginModeOrder;

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

    public Boolean getPasskeyLoginAvailable() {
        return passkeyLoginAvailable;
    }

    public void setPasskeyLoginAvailable(Boolean passkeyLoginAvailable) {
        this.passkeyLoginAvailable = passkeyLoginAvailable;
    }

    public Boolean getPasskeyPasswordlessAvailable() {
        return passkeyPasswordlessAvailable;
    }

    public void setPasskeyPasswordlessAvailable(Boolean passkeyPasswordlessAvailable) {
        this.passkeyPasswordlessAvailable = passkeyPasswordlessAvailable;
    }

    public List<String> getLoginModeOrder() {
        return loginModeOrder;
    }

    public void setLoginModeOrder(List<String> loginModeOrder) {
        this.loginModeOrder = loginModeOrder;
    }
}
