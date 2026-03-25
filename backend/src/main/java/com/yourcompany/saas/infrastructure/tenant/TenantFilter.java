package com.yourcompany.saas.infrastructure.tenant;

import com.yourcompany.saas.common.constant.HeaderConstants;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantId = resolveTenantId(request);
        if (StringUtils.hasText(tenantId)) {
            TenantContext.setTenantId(tenantId);
            MDC.put("tenantId", tenantId);
            response.setHeader(HeaderConstants.TENANT_ID, tenantId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }

    private String resolveTenantId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser && currentUser.getCurrentTenantId() != null) {
            return String.valueOf(currentUser.getCurrentTenantId());
        }
        return request.getHeader(HeaderConstants.TENANT_ID);
    }
}
