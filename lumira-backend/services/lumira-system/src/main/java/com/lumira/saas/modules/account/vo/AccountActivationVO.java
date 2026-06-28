package com.lumira.saas.modules.account.vo;

public final class AccountActivationVO {
    private AccountActivationVO() {
    }

    public static class TokenInfo {
        private boolean valid;
        private String username;
        private String email;
        private String reason;

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
