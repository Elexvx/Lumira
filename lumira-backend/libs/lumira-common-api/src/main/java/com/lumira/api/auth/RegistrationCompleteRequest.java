package com.lumira.api.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationCompleteRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = LoginValidationPatterns.CHINA_MOBILE, message = "请输入有效手机号")
        String mobile,
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "请输入有效邮箱地址")
        @Size(max = 128, message = "邮箱长度不能超过128个字符")
        String email,
        @NotBlank(message = "密码不能为空")
        @Size(max = 8192, message = "密码内容过长")
        String password,
        @NotBlank(message = "图形验证码会话不能为空")
        @Pattern(regexp = LoginValidationPatterns.SYSTEM_TOKEN, message = "图形验证码会话不合法")
        String captchaId,
        @NotBlank(message = "图形验证码不能为空")
        @Pattern(regexp = LoginValidationPatterns.CAPTCHA_CODE, message = "图形验证码只能包含字母和数字")
        String captchaCode,
        @Pattern(regexp = LoginValidationPatterns.SYSTEM_TOKEN, message = "手机验证码会话不合法")
        String mobileChallengeId,
        @Pattern(regexp = "^(?:$|[A-Za-z0-9]{1,12})$", message = "手机验证码格式不合法")
        String mobileVerificationCode,
        @Pattern(regexp = LoginValidationPatterns.SYSTEM_TOKEN, message = "邮箱验证码会话不合法")
        String emailChallengeId,
        @Pattern(regexp = "^(?:$|[A-Za-z0-9]{1,12})$", message = "邮箱验证码格式不合法")
        String emailVerificationCode
) {
    @AssertTrue(message = "手机验证码会话和验证码必须同时提供")
    public boolean isMobileVerificationPairValid() {
        return bothBlankOrBothPresent(mobileChallengeId, mobileVerificationCode);
    }

    @AssertTrue(message = "邮箱验证码会话和验证码必须同时提供")
    public boolean isEmailVerificationPairValid() {
        return bothBlankOrBothPresent(emailChallengeId, emailVerificationCode);
    }

    private static boolean bothBlankOrBothPresent(String first, String second) {
        boolean firstPresent = first != null && !first.isBlank();
        boolean secondPresent = second != null && !second.isBlank();
        return firstPresent == secondPresent;
    }
}
