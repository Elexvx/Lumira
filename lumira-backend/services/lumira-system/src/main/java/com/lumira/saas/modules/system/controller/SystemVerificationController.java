package com.lumira.saas.modules.system.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.auth.dto.SecondFactorVerifyRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/verification")
public class SystemVerificationController {

    private final SystemVerificationAppService verificationAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public SystemVerificationController(
            SystemVerificationAppService verificationAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.verificationAppService = verificationAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/providers")
    public ApiResponse<List<SystemVO.VerificationProviderVO>> providers() {
        CurrentUser currentUser = currentUser();
        requireView();
        return ApiResponse.success(
                verificationAppService.listProviders(requireTenantId(currentUser), currentUser.getUserId()),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/providers/{factorCode}")
    public ApiResponse<SystemVO.VerificationProviderVO> provider(@PathVariable("factorCode") String factorCode) {
        CurrentUser currentUser = currentUser();
        requireView();
        return ApiResponse.success(
                verificationAppService.provider(requireTenantId(currentUser), currentUser.getUserId(), factorCode),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/sms-settings")
    public ApiResponse<SystemVO.SmsVerificationSettingsVO> smsSettings() {
        CurrentUser currentUser = currentUser();
        requireView();
        return ApiResponse.success(
                verificationAppService.getSmsSettings(requireTenantId(currentUser)),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/wechat-settings")
    public ApiResponse<SystemVO.WechatLoginSettingsVO> wechatSettings() {
        CurrentUser currentUser = currentUser();
        requireView();
        return ApiResponse.success(
                verificationAppService.getWechatSettings(requireTenantId(currentUser)),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/passkey-settings")
    public ApiResponse<SystemVO.PasskeySettingsVO> passkeySettings() {
        CurrentUser currentUser = currentUser();
        requireView();
        return ApiResponse.success(
                verificationAppService.getPasskeySettings(requireTenantId(currentUser)),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/settings")
    public ApiResponse<SystemVO.VerificationSettingsVO> verificationSettings() {
        CurrentUser currentUser = currentUser();
        requireView();
        return ApiResponse.success(
                verificationAppService.getVerificationSettings(requireTenantId(currentUser)),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{factorCode}/bind")
    @RepeatSubmit
    public ApiResponse<SystemVO.VerificationChallengeVO> bind(@PathVariable("factorCode") String factorCode) {
        CurrentUser currentUser = currentUser();
        require("system:verification:manage");
        return ApiResponse.success(
                verificationAppService.bind(requireTenantId(currentUser), currentUser.getUserId(), factorCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{factorCode}/unbind")
    @RepeatSubmit
    public ApiResponse<Boolean> unbind(@PathVariable("factorCode") String factorCode) {
        CurrentUser currentUser = currentUser();
        require("system:verification:manage");
        return ApiResponse.success(
                verificationAppService.unbind(requireTenantId(currentUser), currentUser.getUserId(), factorCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{factorCode}/challenge")
    @RepeatSubmit
    public ApiResponse<SystemVO.VerificationChallengeVO> challenge(@PathVariable("factorCode") String factorCode) {
        CurrentUser currentUser = currentUser();
        require("system:verification:manage");
        return ApiResponse.success(
                verificationAppService.challenge(requireTenantId(currentUser), currentUser.getUserId(), factorCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{factorCode}/verify")
    @RepeatSubmit
    public ApiResponse<SystemVO.VerificationVerificationVO> verify(
            @PathVariable("factorCode") String factorCode,
            @Valid @RequestBody SecondFactorVerifyRequest request
    ) {
        CurrentUser currentUser = currentUser();
        require("system:verification:manage");
        if (!factorCode.equalsIgnoreCase(request.getFactorCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证方式不匹配");
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

    @PutMapping("/sms-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmsVerificationSettingsVO> updateSmsSettings(@Valid @RequestBody SystemDTO.SmsVerificationSettingsRequest request) {
        CurrentUser currentUser = currentUser();
        requireConfigManage();
        return ApiResponse.success(
                verificationAppService.updateSmsSettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @DeleteMapping("/sms-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmsVerificationSettingsVO> resetSmsSettings() {
        CurrentUser currentUser = currentUser();
        requireConfigManage();
        return ApiResponse.success(
                verificationAppService.resetSmsSettings(currentUser),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/wechat-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.WechatLoginSettingsVO> updateWechatSettings(@Valid @RequestBody SystemDTO.WechatLoginSettingsRequest request) {
        CurrentUser currentUser = currentUser();
        requireConfigManage();
        return ApiResponse.success(
                verificationAppService.updateWechatSettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @DeleteMapping("/wechat-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.WechatLoginSettingsVO> resetWechatSettings() {
        CurrentUser currentUser = currentUser();
        requireConfigManage();
        return ApiResponse.success(
                verificationAppService.resetWechatSettings(currentUser),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/passkey-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.PasskeySettingsVO> updatePasskeySettings(@Valid @RequestBody SystemDTO.PasskeySettingsRequest request) {
        CurrentUser currentUser = currentUser();
        requireConfigManage();
        return ApiResponse.success(
                verificationAppService.updatePasskeySettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @DeleteMapping("/passkey-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.PasskeySettingsVO> resetPasskeySettings() {
        CurrentUser currentUser = currentUser();
        requireConfigManage();
        return ApiResponse.success(
                verificationAppService.resetPasskeySettings(currentUser),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.VerificationSettingsVO> updateVerificationSettings(@Valid @RequestBody SystemDTO.VerificationSettingsRequest request) {
        CurrentUser currentUser = currentUser();
        requireConfigManage();
        return ApiResponse.success(
                verificationAppService.updateVerificationSettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }

    private Long requireTenantId(CurrentUser currentUser) {
        return com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }

    private void requireView() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (hasAnyPermission(currentUser, "system:verification:view", "system:verification:manage", "system:config:view", "system:config:update")) {
            return;
        }
        permissionGuard.requirePermission(currentUser, "system:verification:view");
    }

    private void requireConfigManage() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (hasAnyPermission(currentUser, "system:verification:manage", "system:config:update")) {
            return;
        }
        permissionGuard.requirePermission(currentUser, "system:verification:manage");
    }

    private boolean hasAnyPermission(CurrentUser currentUser, String... permissionKeys) {
        if (currentUser == null || currentUser.getPermissions() == null) {
            return false;
        }
        if (currentUser.getPermissions().contains("*")) {
            return true;
        }
        for (String permissionKey : permissionKeys) {
            if (currentUser.getPermissions().contains(permissionKey)) {
                return true;
            }
        }
        return false;
    }
}
