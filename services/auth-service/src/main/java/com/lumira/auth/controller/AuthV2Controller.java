package com.lumira.auth.controller;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.auth.LoginEncryptionKeyDTO;
import com.lumira.api.auth.LoginRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.RefreshTokenRequest;
import com.lumira.api.auth.RefreshTokenResponseDTO;
import com.lumira.api.auth.AuthBootstrapDTO;
import com.lumira.auth.service.AuthAppService;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import jakarta.servlet.http.HttpServletRequest;
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

    public AuthV2Controller(AuthAppService authAppService) {
        this.authAppService = authAppService;
    }

    @GetMapping("/login-encryption-key")
    public ApiResponse<LoginEncryptionKeyDTO> loginEncryptionKey() {
        return ApiResponse.success(authAppService.loginEncryptionKey(), TraceContext.getRequestId());
    }

    @PostMapping("/login")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.login(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/refresh-token")
    @RepeatSubmit
    public ApiResponse<RefreshTokenResponseDTO> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authAppService.refreshToken(request), TraceContext.getRequestId());
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
    public ApiResponse<Boolean> logout(HttpServletRequest httpServletRequest) {
        authAppService.logout(httpServletRequest);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/session/keepalive")
    public ApiResponse<Boolean> keepalive() {
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }
}
