package com.lumira.saas.modules.system.export;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class ExportDTO {
    private ExportDTO() {
    }

    public static class UserExportRequest {
        @NotEmpty
        private List<String> fields;
        private Long userId;
        private String username;
        private String mobile;
        private String email;
        private Long deptId;
        private String status;
        private String source;
        private String registeredStart;
        private String registeredEnd;
        private String lastLoginStart;
        private String lastLoginEnd;

        public List<String> getFields() { return fields; }
        public void setFields(List<String> fields) { this.fields = fields; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Long getDeptId() { return deptId; }
        public void setDeptId(Long deptId) { this.deptId = deptId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getRegisteredStart() { return registeredStart; }
        public void setRegisteredStart(String registeredStart) { this.registeredStart = registeredStart; }
        public String getRegisteredEnd() { return registeredEnd; }
        public void setRegisteredEnd(String registeredEnd) { this.registeredEnd = registeredEnd; }
        public String getLastLoginStart() { return lastLoginStart; }
        public void setLastLoginStart(String lastLoginStart) { this.lastLoginStart = lastLoginStart; }
        public String getLastLoginEnd() { return lastLoginEnd; }
        public void setLastLoginEnd(String lastLoginEnd) { this.lastLoginEnd = lastLoginEnd; }
    }
}
