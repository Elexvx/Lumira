package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyService {

    private final SecuritySettingsService securitySettingsService;

    public PasswordPolicyService(SecuritySettingsService securitySettingsService) {
        this.securitySettingsService = securitySettingsService;
    }

    public void validatePassword(String password) {
        SecuritySettingsService.SecuritySettingsSnapshot settings = securitySettingsService.loadSettings();
        String normalizedPassword = password == null ? "" : password;
        if (normalizedPassword.length() < settings.getPasswordMinLength()) {
            throw violation("密码长度不能少于 " + settings.getPasswordMinLength() + " 位");
        }
        if (settings.isPasswordRequireUppercase() && !containsUppercase(normalizedPassword)) {
            throw violation("密码必须包含大写字母");
        }
        if (settings.isPasswordRequireLowercase() && !containsLowercase(normalizedPassword)) {
            throw violation("密码必须包含小写字母");
        }
        if (settings.isPasswordRequireSpecialCharacter() && !containsSpecialCharacter(normalizedPassword)) {
            throw violation("密码必须包含特殊字符");
        }
        if (!settings.isPasswordAllowConsecutiveCharacters() && containsConsecutiveCharacters(normalizedPassword)) {
            throw violation("密码不能包含连续字符");
        }
    }

    private boolean containsUppercase(String value) {
        return value.chars().anyMatch(Character::isUpperCase);
    }

    private boolean containsLowercase(String value) {
        return value.chars().anyMatch(Character::isLowerCase);
    }

    private boolean containsSpecialCharacter(String value) {
        return value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    }

    private boolean containsConsecutiveCharacters(String value) {
        String lower = value.toLowerCase();
        for (int index = 0; index < lower.length() - 2; index++) {
            char first = lower.charAt(index);
            char second = lower.charAt(index + 1);
            char third = lower.charAt(index + 2);
            if (isAscendingSequence(first, second, third) || isDescendingSequence(first, second, third)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAscendingSequence(char first, char second, char third) {
        return isLetterOrDigit(first)
                && isLetterOrDigit(second)
                && isLetterOrDigit(third)
                && second - first == 1
                && third - second == 1
                && sameClass(first, second, third);
    }

    private boolean isDescendingSequence(char first, char second, char third) {
        return isLetterOrDigit(first)
                && isLetterOrDigit(second)
                && isLetterOrDigit(third)
                && first - second == 1
                && second - third == 1
                && sameClass(first, second, third);
    }

    private boolean sameClass(char first, char second, char third) {
        return (Character.isDigit(first) && Character.isDigit(second) && Character.isDigit(third))
                || (Character.isLetter(first) && Character.isLetter(second) && Character.isLetter(third));
    }

    private boolean isLetterOrDigit(char value) {
        return Character.isLetterOrDigit(value);
    }

    private BizException violation(String message) {
        return new BizException(ErrorCode.PASSWORD_POLICY_VIOLATION, message, message);
    }
}
