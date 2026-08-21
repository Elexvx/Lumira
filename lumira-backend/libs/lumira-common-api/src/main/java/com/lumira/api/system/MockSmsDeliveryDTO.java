package com.lumira.api.system;

public class MockSmsDeliveryDTO {

    private String providerCode;
    private String phoneNumbers;
    private String signName;
    private String templateCode;
    private String templateParam;
    private String resultCode;
    private String resultMessage;
    private String requestId;
    private String bizId;

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getPhoneNumbers() { return phoneNumbers; }
    public void setPhoneNumbers(String phoneNumbers) { this.phoneNumbers = phoneNumbers; }
    public String getSignName() { return signName; }
    public void setSignName(String signName) { this.signName = signName; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getTemplateParam() { return templateParam; }
    public void setTemplateParam(String templateParam) { this.templateParam = templateParam; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
}
