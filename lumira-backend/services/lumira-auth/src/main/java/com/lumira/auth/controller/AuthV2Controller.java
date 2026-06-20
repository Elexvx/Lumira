package com.lumira.auth.controller;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.auth.LoginEncryptionKeyDTO;
import com.lumira.api.auth.LoginRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.RefreshTokenRequest;
import com.lumira.api.auth.RefreshTokenResponseDTO;
import com.lumira.api.auth.AuthBootstrapDTO;
import com.lumira.auth.service.AuthCookieService;
import com.lumira.auth.service.AuthAppService;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/auth")
public class AuthV2Controller {

    private final AuthAppService authAppService;
    private final AuthCookieService authCookieService;

    public AuthV2Controller(AuthAppService authAppService, AuthCookieService authCookieService) {
        this.authAppService = authAppService;
        this.authCookieService = authCookieService;
    }

    @GetMapping("/login-encryption-key")
    public ApiResponse<LoginEncryptionKeyDTO> loginEncryptionKey() {
        return ApiResponse.success(authAppService.loginEncryptionKey(), TraceContext.getRequestId());
    }

    @PostMapping("/login")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        LoginResponseDTO response = authAppService.login(request, httpServletRequest);
        authCookieService.writeRefreshToken(httpServletResponse, response.getRefreshToken());
        response.setRefreshToken(null);
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @PostMapping("/refresh-token")
    @RepeatSubmit
    public ApiResponse<RefreshTokenResponseDTO> refreshToken(@RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        authCookieService.validateCsrfIfCookieAuth(httpServletRequest);
        String refreshToken = request != null && request.refreshToken() != null ? request.refreshToken() : authCookieService.readRefreshToken(httpServletRequest);
        RefreshTokenResponseDTO response = authAppService.refreshToken(new RefreshTokenRequest(refreshToken));
        authCookieService.writeRefreshToken(httpServletResponse, response.refreshToken());
        return ApiResponse.success(new RefreshTokenResponseDTO(response.accessToken(), null, response.tokenType(), response.expiresIn()), TraceContext.getRequestId());
    }

    @GetMapping("/current-user")
    public ApiResponse<CurrentUserDTO> currentUser() {
        return ApiResponse.success(authAppService.currentUser(), TraceContext.getRequestId());
    }

    @GetMapping("/bootstrap")
    public ApiResponse<AuthBootstrapDTO> bootstrap() {
        return ApiResponse.success(authAppService.bootstrap(), TraceContext.getRequestId());
    }

    @PostMapping("/logout")
    @RepeatSubmit
    public ApiResponse<Boolean> logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        authCookieService.validateCsrfIfCookieAuth(httpServletRequest);
        authAppService.logout(httpServletRequest);
        authCookieService.clearRefreshToken(httpServletResponse);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/session/keepalive")
    public ApiResponse<Boolean> keepalive() {
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }
}
