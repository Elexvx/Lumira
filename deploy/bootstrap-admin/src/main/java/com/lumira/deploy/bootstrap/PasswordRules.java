package com.lumira.deploy.bootstrap;

final class PasswordRules {

    private PasswordRules() {
    }

    static void validate(char[] password) {
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
