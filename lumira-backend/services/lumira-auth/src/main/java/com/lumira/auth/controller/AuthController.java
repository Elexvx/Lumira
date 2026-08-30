package com.lumira.auth.controller;

import com.lumira.api.auth.*;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.auth.service.AuthCookieService;
import com.lumira.auth.service.AuthAppService;
import com.lumira.auth.service.PasskeyAuthService;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthAppService authAppService;
    private final PasskeyAuthService passkeyAuthService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthAppService authAppService, PasskeyAuthService passkeyAuthService, AuthCookieService authCookieService) {
        this.authAppService = authAppService;
        this.passkeyAuthService = passkeyAuthService;
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
        return loginSuccess(response, httpServletResponse);
    }

    @PostMapping("/login/code/challenge")
    @RepeatSubmit
    public ApiResponse<LoginCodeChallengeDTO> loginCodeChallenge(@Valid @RequestBody LoginCodeChallengeRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.loginCodeChallenge(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/login/code/complete")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> loginCodeComplete(
            @Valid @RequestBody LoginCodeCompleteRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        return loginSuccess(authAppService.completeLoginCodeLogin(request, httpServletRequest), httpServletResponse);
    }

    @PostMapping("/registration/contact/availability")
    @RepeatSubmit
    public ApiResponse<RegistrationContactAvailabilityDTO> registrationContactAvailability(
            @Valid @RequestBody RegistrationContactAvailabilityRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(
                authAppService.registrationContactAvailability(request, httpServletRequest),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/registration/code/challenge")
    @RepeatSubmit
    public ApiResponse<LoginCodeChallengeDTO> registrationCodeChallenge(
            @Valid @RequestBody RegistrationCodeChallengeRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(
                authAppService.registrationCodeChallenge(request, httpServletRequest),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/registration/complete")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> completeRegistration(
            @Valid @RequestBody RegistrationCompleteRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        return loginSuccess(authAppService.completeRegistration(request, httpServletRequest), httpServletResponse);
    }

    @PostMapping("/password-reset/challenge")
    @RepeatSubmit
    public ApiResponse<LoginCodeChallengeDTO> passwordResetChallenge(@Valid @RequestBody PasswordResetChallengeRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.passwordResetChallenge(request, httpServletRequest), TraceContext.getRequestId());
    }

    @PostMapping("/password-reset/complete")
    @RepeatSubmit
    public ApiResponse<Boolean> completePasswordReset(@Valid @RequestBody PasswordResetCompleteRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authAppService.completePasswordReset(request, httpServletRequest), TraceContext.getRequestId());
    }

    @GetMapping("/wechat/authorize-url")
    public ApiResponse<WechatAuthorizeUrlDTO> wechatAuthorizeUrl() {
        return ApiResponse.success(authAppService.wechatAuthorizeUrl(), TraceContext.getRequestId());
    }

    @PostMapping("/wechat/login")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> wechatLogin(
            @Valid @RequestBody WechatLoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        return loginSuccess(authAppService.wechatLogin(request, httpServletRequest), httpServletResponse);
    }

    @PostMapping("/passkeys/authentication/options")
    @RepeatSubmit
    public ApiResponse<PasskeyOptionsDTO> passkeyAuthenticationOptions() {
        return ApiResponse.success(passkeyAuthService.authenticationOptions(), TraceContext.getRequestId());
    }

    @PostMapping("/passkeys/authentication/complete")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> passkeyAuthenticationComplete(
            @Valid @RequestBody PasskeyAuthenticationCompleteRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        return loginSuccess(passkeyAuthService.completeAuthentication(request, httpServletRequest), httpServletResponse);
    }

    @PostMapping("/passkeys/registration/options")
    @RepeatSubmit
    public ApiResponse<PasskeyOptionsDTO> passkeyRegistrationOptions(@RequestBody(required = false) @Valid PasskeyOperationVerificationRequest request) {
        return ApiResponse.success(passkeyAuthService.registrationOptions(request), TraceContext.getRequestId());
    }

    @PostMapping("/passkeys/registration/complete")
    @RepeatSubmit
    public ApiResponse<PasskeyCredentialDTO> passkeyRegistrationComplete(@Valid @RequestBody PasskeyRegistrationCompleteRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(passkeyAuthService.completeRegistration(request, httpServletRequest), TraceContext.getRequestId());
    }

    @GetMapping("/passkeys")
    public ApiResponse<java.util.List<PasskeyCredentialDTO>> passkeyCredentials() {
        return ApiResponse.success(passkeyAuthService.listCredentials(), TraceContext.getRequestId());
    }

    @PatchMapping("/passkeys/{id}")
    @RepeatSubmit
    public ApiResponse<PasskeyCredentialDTO> renamePasskeyCredential(@PathVariable Long id, @Valid @RequestBody PasskeyCredentialRenameRequest request) {
        return ApiResponse.success(passkeyAuthService.renameCredential(id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/passkeys/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deletePasskeyCredential(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid PasskeyOperationVerificationRequest request
    ) {
        return ApiResponse.success(passkeyAuthService.deleteCredential(id, request), TraceContext.getRequestId());
    }

    @PostMapping("/second-factor/complete")
    @RepeatSubmit
    public ApiResponse<LoginResponseDTO> completeSecondFactor(
            @Valid @RequestBody SecondFactorCompleteRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        return loginSuccess(authAppService.completeSecondFactorLogin(request, httpServletRequest), httpServletResponse);
    }

    @PostMapping("/logout")
    @RepeatSubmit
    public ApiResponse<Boolean> logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        authCookieService.validateCsrfIfCookieAuth(httpServletRequest);
        authAppService.logout(httpServletRequest);
        authCookieService.clearRefreshToken(httpServletResponse);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/refresh-token")
    @RepeatSubmit
    public ApiResponse<RefreshTokenResponseDTO> refreshToken(@RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        authCookieService.validateCsrfIfCookieAuth(httpServletRequest);
        String refreshToken = request != null && request.refreshToken() != null ? request.refreshToken() : authCookieService.readRefreshToken(httpServletRequest);
        RefreshTokenResponseDTO response = authAppService.refreshToken(new RefreshTokenRequest(refreshToken));
        authCookieService.writeRefreshToken(httpServletResponse, response.refreshToken());
        return ApiResponse.success(new RefreshTokenResponseDTO(
                response.accessToken(),
                null,
                response.tokenType(),
                response.expiresIn(),
                response.sessionVersion(),
                response.permissionsVersion()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/current-user")
    public ApiResponse<CurrentUserDTO> currentUser(HttpServletResponse httpServletResponse) {
        authCookieService.writeCsrfToken(httpServletResponse);
        return ApiResponse.success(authAppService.currentUser(), TraceContext.getRequestId());
    }

    @PutMapping("/simulated-role")
    @RepeatSubmit
    public ApiResponse<SimulatedRoleSwitchResponseDTO> switchSimulatedRole(
            @RequestBody(required = false) SimulatedRoleSwitchRequest request,
            HttpServletResponse httpServletResponse
    ) {
        SimulatedRoleSwitchResponseDTO response = authAppService.switchSimulatedRole(request);
        authCookieService.writeRefreshToken(httpServletResponse, response.refreshToken());
        return ApiResponse.success(
                new SimulatedRoleSwitchResponseDTO(
                        response.currentUser(),
                        response.accessToken(),
                        null,
                        response.tokenType(),
                        response.expiresIn()
                ),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/session/keepalive")
    public ApiResponse<Boolean> keepalive(HttpServletResponse httpServletResponse) {
        boolean alive = authAppService.keepalive();
        authCookieService.writeCsrfToken(httpServletResponse);
        return ApiResponse.success(alive, TraceContext.getRequestId());
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
    public ApiResponse<com.lumira.api.system.VerificationBindingChallengeDTO> verificationBind(
            @PathVariable String factorCode,
            @RequestBody(required = false) @Valid VerificationBindRequest request
    ) {
        return ApiResponse.success(authAppService.verificationBind(factorCode, request), TraceContext.getRequestId());
    }

    @PostMapping("/verification/providers/{factorCode}/unbind")
    @RepeatSubmit
    public ApiResponse<Boolean> verificationUnbind(
            @PathVariable String factorCode,
            @Valid @RequestBody SecondFactorCompleteRequest request
    ) {
        return ApiResponse.success(authAppService.verificationUnbind(factorCode, request), TraceContext.getRequestId());
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

    private ApiResponse<LoginResponseDTO> loginSuccess(LoginResponseDTO response, HttpServletResponse httpServletResponse) {
        authCookieService.writeRefreshToken(httpServletResponse, response.getRefreshToken());
        response.setRefreshToken(null);
        return ApiResponse.success(response, TraceContext.getRequestId());
    }
}
