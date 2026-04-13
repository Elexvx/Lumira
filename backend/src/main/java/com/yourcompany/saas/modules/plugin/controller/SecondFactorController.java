package com.yourcompany.saas.modules.plugin.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import com.yourcompany.saas.modules.iam.service.PermissionGuard;
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
    private final PermissionGuard permissionGuard;
    private final UserDomainService userDomainService;

    public SecondFactorController(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            UserDomainService userDomainService
    ) {
        this.pluginManagementAppService = pluginManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.userDomainService = userDomainService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<PluginVO.SecondFactorStatusVO>> providers() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        require("plugin:2fa:view");
        return ApiResponse.success(listStatuses(tenantId, currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/providers/{pluginCode}")
    public ApiResponse<PluginVO.SecondFactorStatusVO> provider(@PathVariable("pluginCode") String pluginCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        require("plugin:2fa:view");
        return ApiResponse.success(resolveStatus(tenantId, currentUser.getUserId(), pluginCode), TraceContext.getRequestId());
    }

    @PostMapping("/providers/{pluginCode}/bind")
    public ApiResponse<PluginSecondFactorChallenge> bind(@PathVariable("pluginCode") String pluginCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        require("plugin:2fa:manage");
        PluginRuntimeDescriptor descriptor = requireTenantRuntime(tenantId, pluginCode);
        PluginSecondFactorProvider provider = requireProvider(descriptor);
        SysUserEntity user = requireUser(currentUser);
        if (provider.requiresEmail() && (user.getEmail() == null || user.getEmail().isBlank())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充邮箱后再启用该验证方式");
        }
        PluginSecondFactorChallenge challenge = provider.bind(
                descriptor.getRuntimeContext(),
                tenantId,
                currentUser.getUserId(),
                user.getEmail(),
                user.getMobile()
        );
        persistBindChallenge(descriptor, tenantId, currentUser.getUserId(), challenge.challengeId());
        return ApiResponse.success(challenge, TraceContext.getRequestId());
    }

    @PostMapping("/providers/{pluginCode}/unbind")
    public ApiResponse<Boolean> unbind(@PathVariable("pluginCode") String pluginCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        require("plugin:2fa:manage");
        PluginRuntimeDescriptor descriptor = requireTenantRuntime(tenantId, pluginCode);
        requireProvider(descriptor).unbind(descriptor.getRuntimeContext(), tenantId, currentUser.getUserId());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/providers/{pluginCode}/challenge")
    public ApiResponse<PluginSecondFactorChallenge> challenge(@PathVariable("pluginCode") String pluginCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = requireTenantId(currentUser);
        require("plugin:2fa:manage");
        PluginRuntimeDescriptor descriptor = requireTenantRuntime(tenantId, pluginCode);
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
        require("plugin:2fa:manage");
        PluginRuntimeDescriptor descriptor = requireTenantRuntime(tenantId, pluginCode);
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
        PluginRuntimeDescriptor descriptor;
        try {
            descriptor = requireTenantRuntime(tenantId, pluginCode);
        } catch (BizException exception) {
            PluginVO.SecondFactorStatusVO empty = new PluginVO.SecondFactorStatusVO();
            empty.setPluginCode(pluginCode);
            empty.setStatusMessage(exception.getUserMessage() != null ? exception.getUserMessage() : exception.getErrorMessage());
            return empty;
        }
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

    private PluginRuntimeDescriptor requireTenantRuntime(Long tenantId, String pluginCode) {
        return pluginManagementAppService.requireTenantRuntime(tenantId, pluginCode);
    }

    private PluginSecondFactorProvider requireProvider(PluginRuntimeDescriptor descriptor) {
        PluginSecondFactorProvider provider = descriptor.getSecondFactorProvider();
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件未提供二次验证能力");
        }
        return provider;
    }

    private void persistBindChallenge(PluginRuntimeDescriptor descriptor, Long tenantId, Long userId, String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return;
        }
        descriptor.getRuntimeContext().getJdbcTemplate().update(
                """
                        update plugin_2fa_challenge
                        set deleted = 1, updated_at = current_timestamp
                        where tenant_id = ? and user_id = ? and challenge_type = 'BIND' and deleted = 0
                        """,
                tenantId,
                userId
        );
        descriptor.getRuntimeContext().getJdbcTemplate().update(
                """
                        insert into plugin_2fa_challenge (
                            challenge_id, tenant_id, user_id, challenge_type, expires_at, consumed_flag, created_at, updated_at, deleted
                        ) values (?, ?, ?, 'BIND', date_add(current_timestamp, interval 5 minute), 0, current_timestamp, current_timestamp, 0)
                        on duplicate key update
                            tenant_id = values(tenant_id),
                            user_id = values(user_id),
                            challenge_type = values(challenge_type),
                            expires_at = values(expires_at),
                            consumed_flag = 0,
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                challengeId,
                tenantId,
                userId
        );
    }

    private Long requireTenantId(CurrentUser currentUser) {
        if (currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.TENANT_ERROR, "当前未选择租户");
        }
        return currentUser.getCurrentTenantId();
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }

    private SysUserEntity requireUser(CurrentUser currentUser) {
        return userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
    }
}
