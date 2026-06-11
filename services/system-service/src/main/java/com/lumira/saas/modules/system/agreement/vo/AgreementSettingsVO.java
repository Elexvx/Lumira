package com.lumira.saas.modules.system.agreement.vo;

public class AgreementSettingsVO {

    private String userAgreementMarkdown;
    private String privacyAgreementMarkdown;

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
}
