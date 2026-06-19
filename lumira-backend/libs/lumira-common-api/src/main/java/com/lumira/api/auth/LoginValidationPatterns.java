package com.lumira.api.auth;

public final class LoginValidationPatterns {

    private LoginValidationPatterns() {
    }

    public static final String SAFE_ACCOUNT = "^(?:$|[A-Za-z0-9@._-]{1,128})$";
    public static final String CHINA_MOBILE = "^(?:$|1[3-9]\\d{9})$";
    public static final String LOGIN_TYPE = "^(sms|email|SMS|EMAIL)$";
    public static final String CAPTCHA_CODE = "^(?:$|[A-Za-z0-9]{1,8})$";
    public static final String VERIFICATION_CODE = "^[A-Za-z0-9]{1,12}$";
    public static final String SYSTEM_TOKEN = "^[A-Za-z0-9_-]{1,128}$";
}
