package com.lumira.saas.modules.system.menu.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class MenuReorderRequest {

    @NotEmpty
    @Valid
    private List<MenuOrderItem> items;

    public List<MenuOrderItem> getItems() { return items; }
    public void setItems(List<MenuOrderItem> items) { this.items = items; }
}
