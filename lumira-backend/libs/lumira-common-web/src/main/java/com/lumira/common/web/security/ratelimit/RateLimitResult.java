package com.lumira.common.web.security.ratelimit;

public record RateLimitResult(boolean allowed, long count, long retryAfterSeconds) {
}
