package com.lumira.auth.controller;

import com.lumira.api.auth.*;
import com.lumira.auth.service.AuthAppService;
import com.lumira.auth.service.PasskeyAuthService;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthAppService authAppService;
    private final PasskeyAuthService passkeyAuthService;

    public AuthController(AuthAppService authAppService, PasskeyAuthService passkeyAuthService) {
        this.authAppService = authAppService;
        this.passkeyAuthService = passkeyAuthService;
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

    @PostMapping("/login/code/challenge")
    @RepeatSubmit
    public ApiResponse<LoginCodeChallengeDTO> loginCodeChallenge(@Valid @RequestBody LoginCodeChallengeRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.loginCodeChallenge(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/login/code/complete")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> loginCodeComplete(@Valid @RequestBody LoginCodeCompleteRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.completeLoginCodeLogin(request, httpServletRequest), TraceContext.getRequestId());
    }

    @GetMapping("/wechat/authorize-url")
    public ApiResponse<WechatAuthorizeUrlDTO> wechatAuthorizeUrl() {
        return ApiResponse.success(authAppService.wechatAuthorizeUrl(), TraceContext.getRequestId());
    }

    @PostMapping("/wechat/login")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> wechatLogin(@Valid @RequestBody WechatLoginRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.wechatLogin(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/passkeys/authentication/options")
    @RepeatSubmit
    public ApiResponse<PasskeyOptionsDTO> passkeyAuthenticationOptions() {
        return ApiResponse.success(passkeyAuthService.authenticationOptions(), TraceContext.getRequestId());
    }

    @PostMapping("/passkeys/authentication/complete")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> passkeyAuthenticationComplete(@Valid @RequestBody PasskeyAuthenticationCompleteRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(passkeyAuthService.completeAuthentication(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/passkeys/registration/options")
    @RepeatSubmit
    public ApiResponse<PasskeyOptionsDTO> passkeyRegistrationOptions() {
        return ApiResponse.success(passkeyAuthService.registrationOptions(), TraceContext.getRequestId());
    }

    @PostMapping("/passkeys/registration/complete")
    @RepeatSubmit
    public ApiResponse<com.lumira.api.system.PasskeyCredentialDTO> passkeyRegistrationComplete(@Valid @RequestBody PasskeyRegistrationCompleteRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(passkeyAuthService.completeRegistration(request, httpServletRequest), TraceContext.getRequestId());
    }

    @GetMapping("/passkeys")
    public ApiResponse<java.util.List<com.lumira.api.system.PasskeyCredentialDTO>> passkeyCredentials() {
        return ApiResponse.success(passkeyAuthService.listCredentials(), TraceContext.getRequestId());
    }

    @PatchMapping("/passkeys/{id}")
    @RepeatSubmit
    public ApiResponse<com.lumira.api.system.PasskeyCredentialDTO> renamePasskeyCredential(@PathVariable Long id, @Valid @RequestBody PasskeyCredentialLabelRequest request) {
        return ApiResponse.success(passkeyAuthService.renameCredential(id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/passkeys/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deletePasskeyCredential(@PathVariable Long id) {
        return ApiResponse.success(passkeyAuthService.deleteCredential(id), TraceContext.getRequestId());
    }

    @PostMapping("/second-factor/complete")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> completeSecondFactor(@Valid @RequestBody SecondFactorCompleteRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.completeSecondFactorLogin(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/logout")
    @RepeatSubmit
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

    @PostMapping("/session/keepalive")
    public ApiResponse<Boolean> keepalive() {
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @GetMapping("/verification/providers")
    public ApiResponse<java.util.List<LoginResponseDTO.SecondFactorOptionDTO>> verificationProviders() {
        return ApiResponse.success(authAppService.verificationProviders(), TraceContext.getRequestId());
    }

    @GetMapping("/verification/providers/{factorCode}")
    public ApiResponse<com.lumira.api.system.VerificationProviderDTO> verificationProvider(@PathVariable String factorCode) {
        return ApiResponse.success(authAppService.verificationProvider(factorCode), TraceContext.getRequestId());
    }

    @PostMapping("/verification/providers/{factorCode}/bind")
    @RepeatSubmit
    public ApiResponse<com.lumira.api.system.VerificationChallengeDTO> verificationBind(@PathVariable String factorCode) {
        return ApiResponse.success(authAppService.verificationBind(factorCode), TraceContext.getRequestId());
    }

    @PostMapping("/verification/providers/{factorCode}/unbind")
    @RepeatSubmit
    public ApiResponse<Boolean> verificationUnbind(@PathVariable String factorCode) {
        return ApiResponse.success(authAppService.verificationUnbind(factorCode), TraceContext.getRequestId());
    }

    @PostMapping("/verification/providers/{factorCode}/challenge")
    @RepeatSubmit
    public ApiResponse<com.lumira.api.system.VerificationChallengeDTO> verificationChallenge(@PathVariable String factorCode) {
        return ApiResponse.success(authAppService.verificationChallenge(factorCode), TraceContext.getRequestId());
    }

    @PostMapping("/verification/providers/{factorCode}/verify")
    @RepeatSubmit
    public ApiResponse<com.lumira.api.system.VerificationVerificationDTO> verificationVerify(
            @PathVariable String factorCode,
            @Valid @RequestBody SecondFactorCompleteRequest request
    ) {
        return ApiResponse.success(authAppService.verificationVerify(factorCode, request), TraceContext.getRequestId());
    }
}
