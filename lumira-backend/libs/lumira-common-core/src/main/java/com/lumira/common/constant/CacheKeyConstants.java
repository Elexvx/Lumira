package com.lumira.common.constant;

public final class CacheKeyConstants {

    public static final String PREFIX = "saas";
    public static final String SESSION = "session";
    public static final String TENANT_CONTEXT = "tenant_context";
    public static final String SESSION_USER = "session_user";
    public static final String ONLINE_SESSION_TENANT = "online_session_tenant";
    public static final String ONLINE_SESSION_USER = "online_session_user";
    public static final String ONLINE_SESSION_EVENTS = "online_session_events";
    public static final String LOGIN_CAPTCHA = "login_captcha";
    public static final String LOGIN_ATTEMPT = "login_attempt";
    public static final String LOGIN_FAILURE = "login_failure";
    public static final String WECHAT_LOGIN_STATE = "wechat_login_state";
    public static final String REPEAT_SUBMIT = "repeat_submit";

    private CacheKeyConstants() {
    }

    public static String tenantKey(String tenantId, String suffix) {
        return String.join(":", PREFIX, "tenant", tenantId, suffix);
    }

    public static String userKey(String tenantId, String userId, String suffix) {
        return String.join(":", PREFIX, "tenant", tenantId, "user", userId, suffix);
    }

    public static String sessionKey(String sessionId) {
        return PREFIX + ":" + SESSION + ":" + sessionId;
    }

    public static String sessionOwnerKey(String sessionId) {
        return PREFIX + ":session_owner:" + sessionId;
    }

    public static String userSessionKey(Long userId, String sessionId) {
        return PREFIX + ":" + SESSION_USER + ":" + userId + ":" + sessionId;
    }

    public static String onlineSessionTenantKey(Long tenantId) {
        return String.join(":", PREFIX, ONLINE_SESSION_TENANT, String.valueOf(tenantId));
    }

    public static String onlineSessionUserKey(Long userId) {
        return String.join(":", PREFIX, ONLINE_SESSION_USER, String.valueOf(userId));
    }

    public static String onlineSessionEventsChannel() {
        return String.join(":", PREFIX, ONLINE_SESSION_EVENTS);
    }

    public static String loginCaptchaKey(String captchaId) {
        return String.join(":", PREFIX, LOGIN_CAPTCHA, String.valueOf(captchaId));
    }

    public static String loginAttemptKey(String scope) {
        return String.join(":", PREFIX, LOGIN_ATTEMPT, scope);
    }

    public static String loginFailureKey(String scope) {
        return String.join(":", PREFIX, LOGIN_FAILURE, scope);
    }

    public static String wechatLoginStateKey(String state) {
        return String.join(":", PREFIX, WECHAT_LOGIN_STATE, String.valueOf(state));
    }

    public static String repeatSubmitKey(String scope, String method, String path, String fingerprint) {
        return String.join(":", PREFIX, REPEAT_SUBMIT, scope, method, path, fingerprint);
    }
}
