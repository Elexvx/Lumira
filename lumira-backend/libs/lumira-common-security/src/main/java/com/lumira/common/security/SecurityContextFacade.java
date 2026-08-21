package com.lumira.common.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.Set;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@Component
public class SecurityContextFacade {

    public CurrentUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CurrentUser currentUser)
                || !isTrustedCurrentUser(currentUser)) {
            throw new AuthenticationCredentialsNotFoundException("User not authenticated");
        }
        return currentUser;
    }

    public CurrentUser getCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return null;
        }
        return isTrustedCurrentUser(currentUser) ? currentUser : null;
    }

    public boolean isAuthenticated() {
        CurrentUser currentUser = getCurrentUserOrNull();
        return isTrustedCurrentUser(currentUser);
    }

    public CurrentUser createAnonymousUser() {
        return new CurrentUser(0L, "anonymous", null, 0, false, Set.of());
    }
}
