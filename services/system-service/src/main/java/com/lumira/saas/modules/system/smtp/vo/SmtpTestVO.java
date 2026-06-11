package com.lumira.saas.modules.system.smtp.vo;

public class SmtpTestVO {

    private Boolean success;
    private String message;
    private String toEmail;

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getToEmail() { return toEmail; }
    public void setToEmail(String toEmail) { this.toEmail = toEmail; }
}
