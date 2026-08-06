package com.lumira.saas.modules.system.profile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ProfileFieldSettingsRequest {

    private String pageKey;

    @Valid
    @NotEmpty
    private List<ProfileFieldSettingItem> items;
    private Long expectedConfigVersion;
    private String changeReason;

    public String getPageKey() {
        return pageKey;
    }

    public void setPageKey(String pageKey) {
        this.pageKey = pageKey;
    }

    public List<ProfileFieldSettingItem> getItems() {
        return items;
    }

    public void setItems(List<ProfileFieldSettingItem> items) {
        this.items = items;
    }

    public Long getExpectedConfigVersion() { return expectedConfigVersion; }
    public void setExpectedConfigVersion(Long expectedConfigVersion) { this.expectedConfigVersion = expectedConfigVersion; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
}
