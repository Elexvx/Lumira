package com.legendary.invention.saas.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TenantDTO {

    private TenantDTO() {
    }

    public static class TenantUpsertRequest {
        @NotBlank(message = "tenantCode不能为空")
        @Size(max = 64, message = "tenantCode长度不能超过64个字符")
        private String tenantCode;

        @NotBlank(message = "tenantName不能为空")
        @Size(max = 128, message = "tenantName长度不能超过128个字符")
        private String tenantName;

        @Size(max = 64, message = "tenantShortName长度不能超过64个字符")
        private String tenantShortName;

        @NotBlank(message = "status不能为空")
        @Size(max = 32, message = "status长度不能超过32个字符")
        private String status;

        public String getTenantCode() {
            return tenantCode;
        }

        public void setTenantCode(String tenantCode) {
            this.tenantCode = tenantCode;
        }

        public String getTenantName() {
            return tenantName;
        }

        public void setTenantName(String tenantName) {
            this.tenantName = tenantName;
        }

        public String getTenantShortName() {
            return tenantShortName;
        }

        public void setTenantShortName(String tenantShortName) {
            this.tenantShortName = tenantShortName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
