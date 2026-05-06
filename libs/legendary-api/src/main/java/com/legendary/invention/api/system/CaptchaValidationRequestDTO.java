package com.legendary.invention.api.system;

public record CaptchaValidationRequestDTO(String captchaId, String captchaCode, String captchaProof) {
}
