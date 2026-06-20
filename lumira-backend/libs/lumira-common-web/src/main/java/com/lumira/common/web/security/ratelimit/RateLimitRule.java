package com.lumira.common.web.security.ratelimit;

public record RateLimitRule(String name, int maxAttempts, long windowSeconds) {
}
