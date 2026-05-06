package com.legendary.invention.saas.infrastructure.tenant;

import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final String PLATFORM_TENANT_ID = "1001";

    @Override
    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantId = resolveTenantId(request);
        TenantContext.setTenantId(tenantId);
        MDC.put("tenantId", tenantId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }

    private String resolveTenantId(jakarta.servlet.http.HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser && currentUser.getCurrentTenantId() != null) {
            return String.valueOf(currentUser.getCurrentTenantId());
        }
        return PLATFORM_TENANT_ID;
    }
}
