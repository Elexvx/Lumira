package com.lumira.common.security;

/**
 * Owner boundary for access-token and session authentication.
 *
 * <p>Implementations return {@code null} when credentials are not trusted and
 * may throw a dependency-unavailable business exception when authentication
 * cannot be decided safely.</p>
 */
public interface AccessTokenAuthenticationPort {
    CurrentUser authenticateAccessToken(String token);
}
