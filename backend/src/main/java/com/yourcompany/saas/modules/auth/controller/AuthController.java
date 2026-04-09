package com.yourcompany.saas.modules.auth.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import com.yourcompany.saas.modules.auth.app.AuthAppService;
import com.yourcompany.saas.modules.auth.dto.LoginRequest;
import com.yourcompany.saas.modules.auth.dto.RefreshTokenRequest;
import com.yourcompany.saas.modules.auth.dto.SecondFactorCompleteRequest;
import com.yourcompany.saas.modules.auth.vo.CurrentUserVO;
import com.yourcompany.saas.modules.auth.vo.LoginResponseVO;
import com.yourcompany.saas.modules.auth.vo.RefreshTokenResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthAppService authAppService;
    private final SecurityContextFacade securityContextFacade;

    public AuthController(AuthAppService authAppService, SecurityContextFacade securityContextFacade) {
        this.authAppService = authAppService;
        this.securityContextFacade = securityContextFacade;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponseVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        LoginResponseVO response = authAppService.login(request, resolveClientIp(httpServletRequest), httpServletRequest.getHeader("User-Agent"));
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @PostMapping("/second-factor/complete")
    public ApiResponse<LoginResponseVO> completeSecondFactor(
            @Valid @RequestBody SecondFactorCompleteRequest request,
            HttpServletRequest httpServletRequest
    ) {
        LoginResponseVO response = authAppService.completeSecondFactorLogin(
                request,
                resolveClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        );
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletRequest httpServletRequest) {
        authAppService.logout(securityContextFacade.getCurrentUser(), resolveClientIp(httpServletRequest), httpServletRequest.getHeader("User-Agent"));
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/refresh-token")
    public ApiResponse<RefreshTokenResponseVO> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponseVO response = authAppService.refreshToken(request);
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @GetMapping("/current-user")
    public ApiResponse<CurrentUserVO> currentUser() {
        CurrentUserVO response = authAppService.currentUser(securityContextFacade.getCurrentUser());
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int commaIndex = xff.indexOf(",");
            return commaIndex > 0 ? xff.substring(0, commaIndex).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }
}
