package com.lumira.saas.modules.system.floating.dto;

public class FloatingWindowSettingsRequest {

    private Boolean apiDocsQrEnabled;
    private String apiDocsQrTitle;
    private String apiDocsQrImageUrl;
    private Long expectedConfigVersion;
    private String changeReason;

    public Boolean getApiDocsQrEnabled() {
        return apiDocsQrEnabled;
    }

    public void setApiDocsQrEnabled(Boolean apiDocsQrEnabled) {
        this.apiDocsQrEnabled = apiDocsQrEnabled;
    }

    public String getApiDocsQrTitle() {
        return apiDocsQrTitle;
    }

    public void setApiDocsQrTitle(String apiDocsQrTitle) {
        this.apiDocsQrTitle = apiDocsQrTitle;
    }

    public String getApiDocsQrImageUrl() {
        return apiDocsQrImageUrl;
    }

    public void setApiDocsQrImageUrl(String apiDocsQrImageUrl) {
        this.apiDocsQrImageUrl = apiDocsQrImageUrl;
    }

    public Long getExpectedConfigVersion() { return expectedConfigVersion; }
    public void setExpectedConfigVersion(Long expectedConfigVersion) { this.expectedConfigVersion = expectedConfigVersion; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
}
