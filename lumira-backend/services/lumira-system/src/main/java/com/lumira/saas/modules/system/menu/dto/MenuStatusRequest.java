package com.lumira.saas.modules.system.menu.dto;

import jakarta.validation.constraints.NotBlank;

public class MenuStatusRequest {

    @NotBlank
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
