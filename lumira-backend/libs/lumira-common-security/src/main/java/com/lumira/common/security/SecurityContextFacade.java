package com.lumira.common.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SecurityContextFacade {

    public CurrentUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser) || !currentUser.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("User not authenticated");
        }
        return currentUser;
    }

    public CurrentUser getCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return null;
        }
        return currentUser;
    }

    public boolean isAuthenticated() {
        CurrentUser currentUser = getCurrentUserOrNull();
        return currentUser != null && currentUser.isAuthenticated();
    }

    public CurrentUser createAnonymousUser() {
        return new CurrentUser(0L, "anonymous", null, null, 0, false, Set.of());
    }
}
