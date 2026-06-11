package com.lumira.saas.modules.system.sensitive.security;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordPluginStateService;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordService;
import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SensitiveWordFormFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final SensitiveWordService sensitiveWordService;
    private final SecurityContextFacade securityContextFacade;
    private final ObjectMapper objectMapper;
    private final SensitiveWordPluginStateService pluginStateService;

    public SensitiveWordFormFilter(
            SensitiveWordService sensitiveWordService,
            SecurityContextFacade securityContextFacade,
            ObjectMapper objectMapper,
            SensitiveWordPluginStateService pluginStateService
    ) {
        this.sensitiveWordService = sensitiveWordService;
        this.securityContextFacade = securityContextFacade;
        this.objectMapper = objectMapper;
        this.pluginStateService = pluginStateService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!WRITE_METHODS.contains(request.getMethod().toUpperCase())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!StringUtils.hasText(path)
                || path.startsWith("/api/v1/sensitive-words")
                || path.startsWith("/api/v1/plugins")
                || path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/api/auth/")
                || path.startsWith("/api/public/")) {
            return true;
        }
        String contentType = request.getContentType();
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        return !contentType.toLowerCase().startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        CurrentUser currentUser = securityContextFacade.getCurrentUserOrNull();
        if (currentUser != null && currentUser.isAuthenticated() && pluginStateService.isEnabled(currentUser)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            request.getParameterMap().forEach((key, value) -> {
                if (value == null) {
                    payload.put(key, null);
                } else if (value.length == 1) {
                    payload.put(key, value[0]);
                } else {
                    payload.put(key, value);
                }
            });
            SensitiveWordVO.CheckResult result = sensitiveWordService.checkPayload(currentUser, payload);
            if (result.isHit()) {
                writeError(response, request, result);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request, SensitiveWordVO.CheckResult result) throws IOException {
        String userMessage = sensitiveWordService.formatMatchesForUser(result.getMatches());
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.VALIDATION_ERROR, userMessage, userMessage, TraceContext.getRequestId(), request.getRequestURI());
        response.setStatus(ErrorCode.VALIDATION_ERROR.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
