package com.yourcompany.saas.modules.plugin.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import com.yourcompany.saas.modules.plugin.app.PluginManagementAppService;
import com.yourcompany.saas.modules.plugin.registry.PluginRuntimeDescriptor;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorChallenge;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorProfile;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorVerification;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginSecondFactorProvider;
import com.yourcompany.saas.modules.plugin.vo.PluginVO;
import com.yourcompany.saas.modules.user.domain.UserDomainService;
import com.yourcompany.saas.modules.user.entity.SysUserEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/second-factor")
public class SecondFactorController {

    private final PluginManagementAppService pluginManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final UserDomainService userDomainService;

    public SecondFactorController(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            UserDomainService userDomainService
    ) {
        this.pluginManagementAppService = pluginManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.userDomainService = userDomainService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<PluginVO.SecondFactorStatusVO>> providers() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        return ApiResponse.success(listStatuses(tenantId, currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/providers/{pluginCode}")
    public ApiResponse<PluginVO.SecondFactorStatusVO> provider(@PathVariable("pluginCode") String pluginCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        return ApiResponse.success(resolveStatus(tenantId, currentUser.getUserId(), pluginCode), TraceContext.getRequestId());
    }

    @PostMapping("/providers/{pluginCode}/bind")
    public ApiResponse<PluginSecondFactorChallenge> bind(@PathVariable("pluginCode") String pluginCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        PluginRuntimeDescriptor descriptor = requireDescriptor(tenantId, pluginCode);
        PluginSecondFactorProvider provider = requireProvider(descriptor);
        SysUserEntity user = requireUser(currentUser);
        if (provider.requiresEmail() && (user.getEmail() == null || user.getEmail().isBlank())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充邮箱后再启用该验证方式");
        }
        return ApiResponse.success(
                provider.bind(descriptor.getRuntimeContext(), tenantId, currentUser.getUserId(), user.getEmail(), user.getMobile()),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{pluginCode}/unbind")
    public ApiResponse<Boolean> unbind(@PathVariable("pluginCode") String pluginCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        PluginRuntimeDescriptor descriptor = requireDescriptor(tenantId, pluginCode);
        requireProvider(descriptor).unbind(descriptor.getRuntimeContext(), tenantId, currentUser.getUserId());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/providers/{pluginCode}/challenge")
    public ApiResponse<PluginSecondFactorChallenge> challenge(@PathVariable("pluginCode") String pluginCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        PluginRuntimeDescriptor descriptor = requireDescriptor(tenantId, pluginCode);
        return ApiResponse.success(
                requireProvider(descriptor).prepareChallenge(descriptor.getRuntimeContext(), tenantId, currentUser.getUserId()),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{pluginCode}/verify")
    public ApiResponse<PluginSecondFactorVerification> verify(
            @PathVariable("pluginCode") String pluginCode,
            @Valid @RequestBody com.yourcompany.saas.modules.auth.dto.SecondFactorVerifyRequest request
    ) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        PluginRuntimeDescriptor descriptor = requireDescriptor(tenantId, pluginCode);
        return ApiResponse.success(
                requireProvider(descriptor).verify(descriptor.getRuntimeContext(), request.getChallengeId(), request.getVerificationCode()),
                TraceContext.getRequestId()
        );
    }

    private List<PluginVO.SecondFactorStatusVO> listStatuses(Long tenantId, CurrentUser currentUser) {
        return pluginManagementAppService.availablePlugins(tenantId).stream()
                .map(plugin -> resolveStatus(tenantId, currentUser.getUserId(), plugin.getPluginCode()))
                .filter(status -> status.getPluginCode() != null)
                .toList();
    }

    private PluginVO.SecondFactorStatusVO resolveStatus(Long tenantId, Long userId, String pluginCode) {
        PluginRuntimeDescriptor descriptor = pluginManagementAppService.findTenantRuntimeDescriptor(tenantId, pluginCode).orElse(null);
        if (descriptor == null || descriptor.getSecondFactorProvider() == null) {
            PluginVO.SecondFactorStatusVO empty = new PluginVO.SecondFactorStatusVO();
            empty.setPluginCode(pluginCode);
            return empty;
        }
        PluginSecondFactorProvider provider = descriptor.getSecondFactorProvider();
        PluginSecondFactorProfile profile = provider.profile(descriptor.getRuntimeContext(), tenantId, userId);
        PluginVO.SecondFactorStatusVO status = new PluginVO.SecondFactorStatusVO();
        status.setPluginCode(profile.pluginCode());
        status.setPluginName(profile.pluginName());
        status.setFactorCode(profile.factorCode());
        status.setFactorName(profile.factorName());
        status.setEnabled(profile.enabled());
        status.setBound(profile.bound());
        status.setEmailRequired(profile.emailRequired());
        status.setMaskedContact(profile.maskedContact());
        status.setStatusMessage(profile.statusMessage());
        return status;
    }

    private PluginRuntimeDescriptor requireDescriptor(Long tenantId, String pluginCode) {
        return pluginManagementAppService.findTenantRuntimeDescriptor(tenantId, pluginCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "插件运行时不存在"));
    }

    private PluginSecondFactorProvider requireProvider(PluginRuntimeDescriptor descriptor) {
        PluginSecondFactorProvider provider = descriptor.getSecondFactorProvider();
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件未提供二次验证能力");
        }
        return provider;
    }

    private Long requireTenantId(CurrentUser currentUser) {
        if (currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.TENANT_ERROR, "当前未选择租户");
        }
        return currentUser.getCurrentTenantId();
    }

    private SysUserEntity requireUser(CurrentUser currentUser) {
        return userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
    }
}
