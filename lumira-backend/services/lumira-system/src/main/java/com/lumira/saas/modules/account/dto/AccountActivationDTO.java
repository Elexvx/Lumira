package com.lumira.saas.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AccountActivationDTO {
    private AccountActivationDTO() {
    }

    public static class CompleteRequest {
        @NotBlank
        private String token;
        @NotBlank
        @Size(min = 6, max = 128)
        private String password;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
