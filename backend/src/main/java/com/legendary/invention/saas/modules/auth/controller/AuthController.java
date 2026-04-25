package com.legendary.invention.saas.modules.auth.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.infrastructure.security.ClientIpResolver;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.auth.app.AuthAppService;
import com.legendary.invention.saas.modules.auth.dto.LoginCodeChallengeRequest;
import com.legendary.invention.saas.modules.auth.dto.LoginCodeCompleteRequest;
import com.legendary.invention.saas.modules.auth.app.LoginEncryptionService;
import com.legendary.invention.saas.modules.auth.dto.LoginRequest;
import com.legendary.invention.saas.modules.auth.dto.RefreshTokenRequest;
import com.legendary.invention.saas.modules.auth.dto.SecondFactorCompleteRequest;
import com.legendary.invention.saas.modules.auth.vo.LoginCodeChallengeVO;
import com.legendary.invention.saas.modules.auth.vo.CurrentUserVO;
import com.legendary.invention.saas.modules.auth.vo.LoginResponseVO;
import com.legendary.invention.saas.modules.auth.vo.LoginEncryptionKeyVO;
import com.legendary.invention.saas.modules.auth.vo.RefreshTokenResponseVO;
import com.legendary.invention.saas.modules.system.verification.SystemVerificationAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthAppService authAppService;
    private final LoginEncryptionService loginEncryptionService;
    private final SecurityContextFacade securityContextFacade;
    private final ClientIpResolver clientIpResolver;
    private final SystemVerificationAppService verificationAppService;

    public AuthController(
            AuthAppService authAppService,
            LoginEncryptionService loginEncryptionService,
            SecurityContextFacade securityContextFacade,
            ClientIpResolver clientIpResolver,
            SystemVerificationAppService verificationAppService
    ) {
        this.authAppService = authAppService;
        this.loginEncryptionService = loginEncryptionService;
        this.securityContextFacade = securityContextFacade;
        this.clientIpResolver = clientIpResolver;
        this.verificationAppService = verificationAppService;
    }

    @GetMapping("/login-encryption-key")
    public ApiResponse<LoginEncryptionKeyVO> loginEncryptionKey() {
        return ApiResponse.success(loginEncryptionService.getPublicKeyInfo(), TraceContext.getRequestId());
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponseVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        LoginResponseVO response = authAppService.login(request, clientIpResolver.resolve(httpServletRequest), httpServletRequest.getHeader("User-Agent"));
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @PostMapping("/login/code/challenge")
    public ApiResponse<LoginCodeChallengeVO> loginCodeChallenge(
            @Valid @RequestBody LoginCodeChallengeRequest request,
            HttpServletRequest httpServletRequest
    ) {
        LoginCodeChallengeVO response = authAppService.loginCodeChallenge(
                request,
                clientIpResolver.resolve(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        );
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @PostMapping("/login/code/complete")
    public ApiResponse<LoginResponseVO> loginCodeComplete(
            @Valid @RequestBody LoginCodeCompleteRequest request,
            HttpServletRequest httpServletRequest
    ) {
        LoginResponseVO response = authAppService.completeLoginCodeLogin(
                request,
                clientIpResolver.resolve(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        );
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @PostMapping("/second-factor/complete")
    public ApiResponse<LoginResponseVO> completeSecondFactor(
            @Valid @RequestBody SecondFactorCompleteRequest request,
            HttpServletRequest httpServletRequest
    ) {
        LoginResponseVO response = authAppService.completeSecondFactorLogin(
                request,
                clientIpResolver.resolve(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        );
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletRequest httpServletRequest) {
        authAppService.logout(securityContextFacade.getCurrentUser(), clientIpResolver.resolve(httpServletRequest), httpServletRequest.getHeader("User-Agent"));
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

    @GetMapping("/verification/providers")
    public ApiResponse<java.util.List<com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationProviderVO>> verificationProviders() {
        var currentUser = currentUserOrThrow();
        return ApiResponse.success(
                verificationAppService.listProviders(requireTenantId(currentUser), currentUser.getUserId()),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/verification/providers/{factorCode}")
    public ApiResponse<com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationProviderVO> verificationProvider(@org.springframework.web.bind.annotation.PathVariable("factorCode") String factorCode) {
        var currentUser = currentUserOrThrow();
        return ApiResponse.success(
                verificationAppService.provider(requireTenantId(currentUser), currentUser.getUserId(), factorCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/verification/providers/{factorCode}/bind")
    public ApiResponse<com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationChallengeVO> verificationBind(@org.springframework.web.bind.annotation.PathVariable("factorCode") String factorCode) {
        var currentUser = currentUserOrThrow();
        return ApiResponse.success(
                verificationAppService.bind(requireTenantId(currentUser), currentUser.getUserId(), factorCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/verification/providers/{factorCode}/unbind")
    public ApiResponse<Boolean> verificationUnbind(@org.springframework.web.bind.annotation.PathVariable("factorCode") String factorCode) {
        var currentUser = currentUserOrThrow();
        return ApiResponse.success(
                verificationAppService.unbind(requireTenantId(currentUser), currentUser.getUserId(), factorCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/verification/providers/{factorCode}/challenge")
    public ApiResponse<com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationChallengeVO> verificationChallenge(@org.springframework.web.bind.annotation.PathVariable("factorCode") String factorCode) {
        var currentUser = currentUserOrThrow();
        return ApiResponse.success(
                verificationAppService.challenge(requireTenantId(currentUser), currentUser.getUserId(), factorCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/verification/providers/{factorCode}/verify")
    public ApiResponse<com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationVerificationVO> verificationVerify(
            @org.springframework.web.bind.annotation.PathVariable("factorCode") String factorCode,
            @Valid @RequestBody SecondFactorCompleteRequest request
    ) {
        var currentUser = currentUserOrThrow();
        if (!factorCode.equalsIgnoreCase(request.getFactorCode())) {
            throw new com.legendary.invention.saas.common.exception.BizException(com.legendary.invention.saas.common.enums.ErrorCode.VALIDATION_ERROR, "验证方式不匹配");
        }
        return ApiResponse.success(
                verificationAppService.completeBind(
                        requireTenantId(currentUser),
                        currentUser.getUserId(),
                        factorCode,
                        request.getChallengeId(),
                        request.getVerificationCode()
                ),
                TraceContext.getRequestId()
        );
    }

    private com.legendary.invention.saas.infrastructure.security.CurrentUser currentUserOrThrow() {
        com.legendary.invention.saas.infrastructure.security.CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (currentUser == null) {
            throw new com.legendary.invention.saas.common.exception.BizException(
                    com.legendary.invention.saas.common.enums.ErrorCode.UNAUTHORIZED,
                    "未登录或会话已失效"
            );
        }
        return currentUser;
    }

    private Long requireTenantId(com.legendary.invention.saas.infrastructure.security.CurrentUser currentUser) {
        if (currentUser.getCurrentTenantId() == null) {
            throw new com.legendary.invention.saas.common.exception.BizException(
                    com.legendary.invention.saas.common.enums.ErrorCode.TENANT_ERROR,
                    "当前未选择租户"
            );
        }
        return currentUser.getCurrentTenantId();
    }
}
