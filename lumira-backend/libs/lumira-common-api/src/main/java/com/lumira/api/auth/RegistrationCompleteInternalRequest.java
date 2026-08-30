package com.lumira.api.auth;

public record RegistrationCompleteInternalRequest(
        String mobile,
        String email,
        String rawPassword,
        String captchaId,
        String captchaCode,
        String mobileChallengeId,
        String mobileVerificationCode,
        String emailChallengeId,
        String emailVerificationCode,
        String registrationIp,
        String userAgent
) {
}
