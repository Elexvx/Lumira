package com.lumira.saas.modules.system.profile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ProfileFieldSettingsRequest {

    @Valid
    @NotEmpty
    private List<ProfileFieldSettingItem> items;

    public List<ProfileFieldSettingItem> getItems() {
        return items;
    }

    public void setItems(List<ProfileFieldSettingItem> items) {
        this.items = items;
    }
}
