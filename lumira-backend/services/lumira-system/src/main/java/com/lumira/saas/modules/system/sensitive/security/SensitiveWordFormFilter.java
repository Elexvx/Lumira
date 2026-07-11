package com.lumira.saas.modules.system.sensitive.security;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
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
        if (SensitiveWordRequestSkipMatcher.shouldSkipPath(path)) {
            return true;
        }
        return SensitiveWordRequestSkipMatcher.shouldSkipFormUrlEncoded(request.getContentType());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        CurrentUser currentUser = securityContextFacade.getCurrentUserOrNull();
        try {
            if (isTrustedCurrentUser(currentUser) && pluginStateService.isEnabled(currentUser)) {
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
                SensitiveWordVO.CheckResult result = sensitiveWordService.checkPayloadForSubmission(currentUser, payload);
                if (result.isBlocked()) {
                    writeError(response, request, result);
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } catch (BizException exception) {
            if (!response.isCommitted()) {
                writeBizError(response, request, exception);
                return;
            }
            throw exception;
        }
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request, SensitiveWordVO.CheckResult result) throws IOException {
        String userMessage = sensitiveWordService.formatMatchesForUser(result.getMatches());
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.VALIDATION_ERROR, userMessage, userMessage, TraceContext.getRequestId(), request.getRequestURI());
        response.setStatus(ErrorCode.VALIDATION_ERROR.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void writeBizError(HttpServletResponse response, HttpServletRequest request, BizException exception) throws IOException {
        ApiResponse<Void> body = ApiResponse.fail(
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getUserMessage(),
                TraceContext.getRequestId(),
                request.getRequestURI()
        );
        response.setStatus(exception.getErrorCode().getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
