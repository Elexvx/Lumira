package com.lumira.saas.modules.system.smtp.dto;

import jakarta.validation.constraints.NotBlank;

public class SmtpTestRequest {

    @NotBlank
    private String toEmail;
    private String subject;
    private String content;

    public String getToEmail() { return toEmail; }
    public void setToEmail(String toEmail) { this.toEmail = toEmail; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
