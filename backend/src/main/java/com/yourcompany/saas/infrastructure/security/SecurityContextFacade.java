package com.yourcompany.saas.infrastructure.security;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SecurityContextFacade {

    public CurrentUser getCurrentUser() {
        return CurrentUser.builder()
                .userId(0L)
                .username("anonymous")
                .tenantId("default")
                .permissions(Set.of())
                .build();
    }
}
