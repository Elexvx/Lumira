package com.lumira.api.system;

/** Sensitive challenge response payload; never encode verification codes in a query string. */
public record VerificationCodeCheckRequest(String challengeId, String verificationCode) {
}
