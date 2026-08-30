package com.lumira.saas.modules.system.update.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Server-side write barrier for updater-owned READ_ONLY and drain windows. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class PlatformUpdateWriteGuardFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final PlatformUpdateMaintenanceService maintenanceService;

    public PlatformUpdateWriteGuardFilter(PlatformUpdateMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var state = maintenanceService.currentMode();
        if (SAFE_METHODS.contains(request.getMethod()) || "NORMAL".equals(state.mode()) || isMaintenanceControl(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Retry-After", "5");
        String reason = state.reason() == null ? "Platform update write barrier is active" : state.reason();
        response.getWriter().write("{\"code\":50301,\"message\":\"" + jsonEscape(reason)
                + "\",\"maintenanceMode\":\"" + state.mode() + "\"}");
    }

    private boolean isMaintenanceControl(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/system/update") || path.startsWith("/internal/update");
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
    }
}
