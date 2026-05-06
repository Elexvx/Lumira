package com.legendary.invention.auth.controller;

import com.legendary.invention.api.auth.*;
import com.legendary.invention.auth.service.AuthAppService;
import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.web.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthAppService authAppService;

    public AuthController(AuthAppService authAppService) {
        this.authAppService = authAppService;
    }

    @GetMapping("/login-encryption-key")
    public ApiResponse<LoginEncryptionKeyDTO> loginEncryptionKey() {
        return ApiResponse.success(authAppService.loginEncryptionKey(), TraceContext.getRequestId());
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponseDTO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.login(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/login/code/challenge")
    public ApiResponse<LoginCodeChallengeDTO> loginCodeChallenge(@Valid @RequestBody LoginCodeChallengeRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.loginCodeChallenge(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/login/code/complete")
    public ApiResponse<LoginResponseDTO> loginCodeComplete(@Valid @RequestBody LoginCodeCompleteRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.completeLoginCodeLogin(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/second-factor/complete")
    public ApiResponse<LoginResponseDTO> completeSecondFactor(@Valid @RequestBody SecondFactorCompleteRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.completeSecondFactorLogin(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletRequest httpServletRequest) {
        authAppService.logout(httpServletRequest);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/refresh-token")
    public ApiResponse<RefreshTokenResponseDTO> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authAppService.refreshToken(request), TraceContext.getRequestId());
    }

    @GetMapping("/current-user")
    public ApiResponse<CurrentUserDTO> currentUser() {
        return ApiResponse.success(authAppService.currentUser(), TraceContext.getRequestId());
    }

    @GetMapping("/verification/providers")
    public ApiResponse<java.util.List<LoginResponseDTO.SecondFactorOptionDTO>> verificationProviders() {
        return ApiResponse.success(authAppService.verificationProviders(), TraceContext.getRequestId());
    }

    @GetMapping("/verification/providers/{factorCode}")
    public ApiResponse<com.legendary.invention.api.system.VerificationProviderDTO> verificationProvider(@PathVariable String factorCode) {
        return ApiResponse.success(authAppService.verificationProvider(factorCode), TraceContext.getRequestId());
    }

    @PostMapping("/verification/providers/{factorCode}/bind")
    public ApiResponse<com.legendary.invention.api.system.VerificationChallengeDTO> verificationBind(@PathVariable String factorCode) {
        return ApiResponse.success(authAppService.verificationBind(factorCode), TraceContext.getRequestId());
    }

    @PostMapping("/verification/providers/{factorCode}/unbind")
    public ApiResponse<Boolean> verificationUnbind(@PathVariable String factorCode) {
        return ApiResponse.success(authAppService.verificationUnbind(factorCode), TraceContext.getRequestId());
    }

    @PostMapping("/verification/providers/{factorCode}/challenge")
    public ApiResponse<com.legendary.invention.api.system.VerificationChallengeDTO> verificationChallenge(@PathVariable String factorCode) {
        return ApiResponse.success(authAppService.verificationChallenge(factorCode), TraceContext.getRequestId());
    }

    @PostMapping("/verification/providers/{factorCode}/verify")
    public ApiResponse<com.legendary.invention.api.system.VerificationVerificationDTO> verificationVerify(
            @PathVariable String factorCode,
            @Valid @RequestBody SecondFactorCompleteRequest request
    ) {
        return ApiResponse.success(authAppService.verificationVerify(factorCode, request), TraceContext.getRequestId());
    }
}
