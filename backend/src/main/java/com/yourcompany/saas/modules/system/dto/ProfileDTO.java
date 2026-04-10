package com.yourcompany.saas.modules.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class ProfileDTO {

    private ProfileDTO() {
    }

    public static class EmailUpdateRequest {
        @NotBlank(message = "请输入邮箱")
        @Email(message = "请输入有效邮箱地址")
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
