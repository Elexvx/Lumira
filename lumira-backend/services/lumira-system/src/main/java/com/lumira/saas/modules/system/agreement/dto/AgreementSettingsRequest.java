package com.lumira.saas.modules.system.agreement.dto;

public class AgreementSettingsRequest {

    private String userAgreementMarkdown;
    private String privacyAgreementMarkdown;
    private Long expectedConfigVersion;
    private String changeReason;

    public String getUserAgreementMarkdown() {
        return userAgreementMarkdown;
    }

    public void setUserAgreementMarkdown(String userAgreementMarkdown) {
        this.userAgreementMarkdown = userAgreementMarkdown;
    }

    public String getPrivacyAgreementMarkdown() {
        return privacyAgreementMarkdown;
    }

    public void setPrivacyAgreementMarkdown(String privacyAgreementMarkdown) {
        this.privacyAgreementMarkdown = privacyAgreementMarkdown;
    }

    public Long getExpectedConfigVersion() { return expectedConfigVersion; }
    public void setExpectedConfigVersion(Long expectedConfigVersion) { this.expectedConfigVersion = expectedConfigVersion; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
}
