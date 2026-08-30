package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationContactAvailabilityRequest(
        @NotBlank(message = "联系方式类型不能为空")
        @Pattern(regexp = "^(MOBILE|EMAIL|mobile|email)$", message = "联系方式类型不合法")
        String contactType,
        @NotBlank(message = "联系方式不能为空")
        @Size(max = 128, message = "联系方式长度不能超过128个字符")
        @Pattern(regexp = LoginValidationPatterns.REGISTRATION_CONTACT, message = "联系方式包含不允许的字符")
        String contact
) {
}
