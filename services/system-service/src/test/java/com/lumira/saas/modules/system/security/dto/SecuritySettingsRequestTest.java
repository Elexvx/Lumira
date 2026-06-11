package com.lumira.saas.modules.system.security.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecuritySettingsRequestTest {

    @Test
    void deserializesPasswordPolicyFields() throws Exception {
        String payload = """
                {
                  "idleTimeoutSeconds": 1800,
                  "accessTokenExpireSeconds": 1800,
                  "refreshTokenExpireSeconds": 604800,
                  "allowMultiDeviceLogin": true,
                  "captchaEnabled": false,
                  "captchaType": "IMAGE",
                  "loginDefenseWindowMinutes": 5,
                  "loginMaxValidationAttempts": 100,
                  "loginMaxFailureCount": 10,
                  "verificationCodeExpireSeconds": 300,
                  "verificationCodeCooldownSeconds": 60,
                  "passwordMinLength": 8,
                  "passwordRequireUppercase": true,
                  "passwordRequireLowercase": true,
                  "passwordRequireSpecialCharacter": false,
                  "passwordAllowConsecutiveCharacters": true
                }
                """;

        SecuritySettingsRequest request = new ObjectMapper().readValue(payload, SecuritySettingsRequest.class);

        assertEquals(8L, request.getPasswordMinLength());
        assertTrue(request.getPasswordRequireUppercase());
        assertTrue(request.getPasswordRequireLowercase());
        assertFalse(request.getPasswordRequireSpecialCharacter());
        assertTrue(request.getPasswordAllowConsecutiveCharacters());
    }
}
