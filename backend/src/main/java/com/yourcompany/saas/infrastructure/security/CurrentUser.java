package com.yourcompany.saas.infrastructure.security;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class CurrentUser {
    private Long userId;
    private String username;
    private String tenantId;
    private Set<String> permissions;
}
