package com.lumira.deploy.bootstrap;

import java.util.Arrays;

final class PasswordRules {

    private PasswordRules() {
    }

    private static final char[] LOCAL_DEFAULT_PASSWORD = {'1', '2', '3', '4', '5', '6'};

    static void validate(char[] password, String initializationSource) {
        if ("LOCAL_DEFAULT".equals(initializationSource)) {
            if (!Arrays.equals(password, LOCAL_DEFAULT_PASSWORD)) {
                throw new IllegalArgumentException("Local default administrator password is invalid");
            }
            return;
        }
        if (password.length < 12 || password.length > 128) {
            throw new IllegalArgumentException("Bootstrap administrator password must contain 12 to 128 characters");
        }
        boolean uppercase = false;
        boolean lowercase = false;
        boolean digit = false;
        boolean special = false;
        for (char character : password) {
            uppercase |= Character.isUpperCase(character);
            lowercase |= Character.isLowerCase(character);
            digit |= Character.isDigit(character);
            special |= !Character.isLetterOrDigit(character);
        }
        if (!uppercase || !lowercase || !digit || !special) {
            throw new IllegalArgumentException(
                    "Bootstrap administrator password must include upper-case, lower-case, numeric, and special characters"
            );
        }
    }
}
