package com.lumira.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextFacadeTest {

    private final SecurityContextFacade facade = new SecurityContextFacade();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserShouldRejectBlankUsernamePrincipal() {
        CurrentUser currentUser = trustedUser();
        currentUser.setUsername(" ");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, "n/a", Set.of())
        );

        assertThatThrownBy(facade::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(facade.getCurrentUserOrNull()).isNull();
        assertThat(facade.isAuthenticated()).isFalse();
    }

    @Test
    void getCurrentUserShouldRejectMissingSessionVersionPrincipal() {
        CurrentUser currentUser = trustedUser();
        currentUser.setSessionVersion(null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, "n/a", Set.of())
        );

        assertThatThrownBy(facade::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(facade.getCurrentUserOrNull()).isNull();
        assertThat(facade.isAuthenticated()).isFalse();
    }

    @Test
    void getCurrentUserShouldRejectMissingUserUuidPrincipal() {
        CurrentUser currentUser = trustedUser();
        currentUser.setUserUuid(null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, "n/a", Set.of())
        );

        assertThatThrownBy(facade::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(facade.getCurrentUserOrNull()).isNull();
        assertThat(facade.isAuthenticated()).isFalse();
    }

    @Test
    void getCurrentUserShouldRejectMissingPermissionsVersionPrincipal() {
        CurrentUser currentUser = trustedUser();
        currentUser.setPermissionsVersion(null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, "n/a", Set.of())
        );

        assertThatThrownBy(facade::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(facade.getCurrentUserOrNull()).isNull();
        assertThat(facade.isAuthenticated()).isFalse();
    }

    @Test
    void getCurrentUserShouldReturnTrustedPrincipal() {
        CurrentUser currentUser = trustedUser();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, "n/a", Set.of())
        );

        assertThat(facade.getCurrentUser()).isSameAs(currentUser);
        assertThat(facade.getCurrentUserOrNull()).isSameAs(currentUser);
        assertThat(facade.isAuthenticated()).isTrue();
    }

    private CurrentUser trustedUser() {
        CurrentUser currentUser = new CurrentUser(100L, "admin", "session-1", 1, true, Set.of("*"));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }
}
