package com.lumira.saas.modules.system.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UserStatusRequest {

    @NotBlank(message = "用户状态不能为空")
    @Pattern(regexp = "^(ENABLED|DISABLED|enabled|disabled)$", message = "用户状态只能是 ENABLED 或 DISABLED")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status == null ? null : status.trim().toUpperCase(); }
}
