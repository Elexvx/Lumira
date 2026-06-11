package com.lumira.api.system;

public record CaptchaValidationRequestDTO(String captchaId, String captchaCode, String captchaProof) {
}
