package com.lumira.auth.service;

import com.lumira.auth.model.AuthSession;

import java.time.Duration;
import java.time.Instant;

public final class AuthSessionIdlePolicy {

    private AuthSessionIdlePolicy() {
    }

    public static boolean isIdleExpired(AuthSession session, long idleTimeoutSeconds, Instant now) {
        if (session == null || idleTimeoutSeconds <= 0) {
            return false;
        }
        Instant activityAt = session.getLastActivityAt();
        if (activityAt == null) {
            return false;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        return Duration.between(activityAt, effectiveNow).compareTo(Duration.ofSeconds(idleTimeoutSeconds)) >= 0;
    }
}
