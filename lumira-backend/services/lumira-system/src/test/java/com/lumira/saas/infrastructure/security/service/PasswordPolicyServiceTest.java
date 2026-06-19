package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyServiceTest {

    @Test
    void shouldAcceptPasswordThatMatchesPolicy() {
        PasswordPolicyService service = new PasswordPolicyService(stubSecuritySettingsService(settings -> {
            settings.setPasswordMinLength(6);
            settings.setPasswordRequireUppercase(true);
            settings.setPasswordRequireLowercase(true);
            settings.setPasswordRequireSpecialCharacter(true);
            settings.setPasswordAllowConsecutiveCharacters(true);
        }));

        assertDoesNotThrow(() -> service.validatePassword("Abcdef!"));
    }

    @Test
    void shouldRejectPasswordMissingRequiredCharacterClass() {
        PasswordPolicyService service = new PasswordPolicyService(stubSecuritySettingsService(settings -> {
            settings.setPasswordMinLength(6);
            settings.setPasswordRequireUppercase(true);
            settings.setPasswordRequireLowercase(true);
            settings.setPasswordRequireSpecialCharacter(true);
            settings.setPasswordAllowConsecutiveCharacters(true);
        }));

        BizException exception = assertThrows(BizException.class, () -> service.validatePassword("abcdef!"));

        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.getErrorCode());
        assertEquals("密码必须包含大写字母", exception.getMessage());
    }

    @Test
    void shouldRejectPasswordWithConsecutiveCharactersWhenDisabled() {
        PasswordPolicyService service = new PasswordPolicyService(stubSecuritySettingsService(settings -> {
            settings.setPasswordMinLength(6);
            settings.setPasswordRequireUppercase(false);
            settings.setPasswordRequireLowercase(false);
            settings.setPasswordRequireSpecialCharacter(false);
            settings.setPasswordAllowConsecutiveCharacters(false);
        }));

        BizException exception = assertThrows(BizException.class, () -> service.validatePassword("Abc123!"));

        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.getErrorCode());
        assertEquals("密码不能包含连续字符", exception.getMessage());
    }

    private SecuritySettingsService stubSecuritySettingsService(java.util.function.Consumer<SecuritySettingsService.SecuritySettingsSnapshot> customizer) {
        SecuritySettingsService.SecuritySettingsSnapshot snapshot = new SecuritySettingsService.SecuritySettingsSnapshot();
        snapshot.setPasswordMinLength(6);
        snapshot.setPasswordRequireUppercase(false);
        snapshot.setPasswordRequireLowercase(false);
        snapshot.setPasswordRequireSpecialCharacter(false);
        snapshot.setPasswordAllowConsecutiveCharacters(true);
        snapshot.setLoginDefenseWindowMinutes(5);
        snapshot.setLoginMaxValidationAttempts(100);
        snapshot.setLoginMaxFailureCount(10);
        customizer.accept(snapshot);
        return new SecuritySettingsService(null, null) {
            @Override
            public SecuritySettingsSnapshot loadSettings() {
                return snapshot;
            }
        };
    }
}
