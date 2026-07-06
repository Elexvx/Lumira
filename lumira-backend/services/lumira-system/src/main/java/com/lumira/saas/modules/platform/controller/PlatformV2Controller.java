package com.lumira.saas.modules.platform.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.app.OnlineSessionManagementAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.platform.app.PlatformBootstrapService;
import com.lumira.saas.modules.system.vo.SystemVO;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v2/platform")
public class PlatformV2Controller {

    private final SystemManagementAppService systemManagementAppService;
    private final SystemVerificationAppService systemVerificationAppService;
    private final OnlineSessionManagementAppService onlineSessionManagementAppService;
    private final PlatformBootstrapService platformBootstrapService;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public PlatformV2Controller(
            SystemManagementAppService systemManagementAppService,
            SystemVerificationAppService systemVerificationAppService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            PlatformBootstrapService platformBootstrapService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.systemVerificationAppService = systemVerificationAppService;
        this.onlineSessionManagementAppService = onlineSessionManagementAppService;
        this.platformBootstrapService = platformBootstrapService;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/public/bootstrap")
    public ApiResponse<SystemVO.PublicBootstrapVO> publicBootstrap() {
        return recordPlatformBootstrap(() -> {
            return ApiResponse.success(platformBootstrapService.getPublicBootstrap(), TraceContext.getRequestId());
        });
    }

    @GetMapping("/configs")
    public ApiResponse<PageResponse<SystemVO.ConfigVO>> configs(
            @RequestParam(name = "configKey", required = false) String configKey,
            @RequestParam(name = "configName", required = false) String configName,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("system:config:view");
        return recordPlatformConfigRead(() ->
                ApiResponse.success(
                        systemManagementAppService.listConfigs(currentUser, configKey, configName, pageNo, pageSize),
                        TraceContext.getRequestId()
                )
        );
    }

    @GetMapping("/configs/{id}")
    public ApiResponse<SystemVO.ConfigVO> config(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:config:view");
        return recordPlatformConfigRead(() ->
                ApiResponse.success(systemManagementAppService.getConfig(currentUser, id), TraceContext.getRequestId())
        );
    }

    @PostMapping("/configs")
    @RepeatSubmit
    public ApiResponse<SystemVO.ConfigVO> createConfig(@Valid @RequestBody SystemDTO.ConfigUpsertRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(systemManagementAppService.createConfig(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/configs/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.ConfigVO> updateConfig(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.ConfigUpsertRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateConfig(currentUser, id, request), TraceContext.getRequestId());
    }

    @GetMapping("/dict-types")
    public ApiResponse<PageResponse<SystemVO.DictTypeVO>> dictTypes(
            @RequestParam(name = "dictCode", required = false) String dictCode,
            @RequestParam(name = "dictName", required = false) String dictName,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("system:dict:view");
        return ApiResponse.success(
                systemManagementAppService.listDictTypes(currentUser, dictCode, dictName, status, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/dict-types/{id}")
    public ApiResponse<SystemVO.DictTypeVO> dictType(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:dict:view");
        return ApiResponse.success(systemManagementAppService.getDictType(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/dict-types")
    @RepeatSubmit
    public ApiResponse<SystemVO.DictTypeVO> createDictType(@Valid @RequestBody SystemDTO.DictTypeUpsertRequest request) {
        CurrentUser currentUser = require("system:dict:create");
        return ApiResponse.success(systemManagementAppService.createDictType(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/dict-types/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.DictTypeVO> updateDictType(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.DictTypeUpsertRequest request) {
        CurrentUser currentUser = require("system:dict:update");
        return ApiResponse.success(systemManagementAppService.updateDictType(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/dict-types/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteDictType(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:dict:delete");
        return ApiResponse.success(systemManagementAppService.deleteDictType(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/dict-types/{id}/items")
    public ApiResponse<List<SystemVO.DictItemVO>> dictItems(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:dict:view");
        return ApiResponse.success(systemManagementAppService.listDictItems(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/dict-types/{id}/items")
    @RepeatSubmit
    public ApiResponse<SystemVO.DictItemVO> createDictItem(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.DictItemUpsertRequest request) {
        CurrentUser currentUser = require("system:dict:create");
        return ApiResponse.success(systemManagementAppService.createDictItem(currentUser, id, request), TraceContext.getRequestId());
    }

    @PutMapping("/dict-types/{dictTypeId}/items/{itemId}")
    @RepeatSubmit
    public ApiResponse<SystemVO.DictItemVO> updateDictItem(
            @PathVariable("dictTypeId") Long dictTypeId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody SystemDTO.DictItemUpsertRequest request
    ) {
        CurrentUser currentUser = require("system:dict:update");
        return ApiResponse.success(systemManagementAppService.updateDictItem(currentUser, dictTypeId, itemId, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/dict-types/{dictTypeId}/items/{itemId}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteDictItem(
            @PathVariable("dictTypeId") Long dictTypeId,
            @PathVariable("itemId") Long itemId
    ) {
        CurrentUser currentUser = require("system:dict:delete");
        return ApiResponse.success(systemManagementAppService.deleteDictItem(currentUser, dictTypeId, itemId), TraceContext.getRequestId());
    }

    @GetMapping("/runtime-appearance-settings")
    public ApiResponse<SystemVO.RuntimeAppearanceSettingsVO> runtimeAppearanceSettings() {
        return recordPlatformConfigRead(() ->
                ApiResponse.success(
                        systemManagementAppService.getPublicRuntimeAppearanceSettings(),
                        TraceContext.getRequestId()
                )
        );
    }

    @GetMapping("/branding-settings")
    public ApiResponse<SystemVO.BrandingSettingsVO> brandingSettings() {
        return recordPlatformConfigRead(() ->
                ApiResponse.success(
                        systemManagementAppService.getPublicBrandingSettings(),
                        TraceContext.getRequestId()
                )
        );
    }

    @PutMapping("/branding-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.BrandingSettingsVO> updateBrandingSettings(@RequestBody SystemDTO.BrandingSettingsRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(
                systemManagementAppService.updateBrandingSettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/agreement-settings")
    public ApiResponse<SystemVO.AgreementSettingsVO> agreementSettings() {
        return ApiResponse.success(systemManagementAppService.getPublicAgreementSettings(), TraceContext.getRequestId());
    }

    @PutMapping("/agreement-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.AgreementSettingsVO> updateAgreementSettings(@RequestBody SystemDTO.AgreementSettingsRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(
                systemManagementAppService.updateAgreementSettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/watermark-settings")
    public ApiResponse<SystemVO.WatermarkSettingsVO> watermarkSettings() {
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getWatermarkSettings(currentUser), TraceContext.getRequestId());
    }

    @PutMapping("/watermark-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.WatermarkSettingsVO> updateWatermarkSettings(@RequestBody SystemDTO.WatermarkSettingsRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateWatermarkSettings(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/floating-window-settings")
    public ApiResponse<SystemVO.FloatingWindowSettingsVO> floatingWindowSettings() {
        return ApiResponse.success(systemManagementAppService.getPublicFloatingWindowSettings(), TraceContext.getRequestId());
    }

    @PutMapping("/floating-window-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.FloatingWindowSettingsVO> updateFloatingWindowSettings(@RequestBody SystemDTO.FloatingWindowSettingsRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateFloatingWindowSettings(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/security-settings")
    public ApiResponse<SystemVO.SecuritySettingsVO> securitySettings() {
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getSecuritySettings(currentUser), TraceContext.getRequestId());
    }

    @PutMapping("/security-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.SecuritySettingsVO> updateSecuritySettings(@Valid @RequestBody SystemDTO.SecuritySettingsRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(
                systemManagementAppService.updateSecuritySettings(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/smtp-settings")
    public ApiResponse<SystemVO.SmtpSettingsVO> smtpSettings() {
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getSmtpSettings(currentUser), TraceContext.getRequestId());
    }

    @PutMapping("/smtp-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmtpSettingsVO> updateSmtpSettings(@Valid @RequestBody SystemDTO.SmtpSettingsRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateSmtpSettings(currentUser, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/smtp-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmtpSettingsVO> resetSmtpSettings() {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(systemManagementAppService.resetSmtpSettings(currentUser), TraceContext.getRequestId());
    }

    @PostMapping("/smtp-settings/test")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmtpTestVO> testSmtpSettings(@Valid @RequestBody SystemDTO.SmtpTestRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(systemManagementAppService.testSmtpSettings(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/notification/wechat-official-settings")
    public ApiResponse<SystemVO.WechatOfficialAccountSettingsVO> wechatOfficialAccountSettings() {
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getWechatOfficialAccountSettings(currentUser), TraceContext.getRequestId());
    }

    @PutMapping("/notification/wechat-official-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.WechatOfficialAccountSettingsVO> updateWechatOfficialAccountSettings(@Valid @RequestBody SystemDTO.WechatOfficialAccountSettingsRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateWechatOfficialAccountSettings(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/audit/summary")
    public ApiResponse<Map<String, Integer>> auditSummary() {
        CurrentUser currentUser = require("audit:view");
        PageResponse<SystemVO.AuditLogVO> login = systemManagementAppService.listLoginLogs(currentUser, null, 1, 1);
        PageResponse<SystemVO.AuditLogVO> operation = systemManagementAppService.listOperationLogs(currentUser, null, 1, 1);
        return ApiResponse.success(
                Map.of(
                        "loginCount", (int) login.getTotal(),
                        "operationCount", (int) operation.getTotal()
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/audit/login-logs")
    public ApiResponse<PageResponse<SystemVO.AuditLogVO>> loginLogs(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "loginType", required = false) String loginType,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("audit:login:view");
        return ApiResponse.success(
                systemManagementAppService.listLoginLogs(currentUser, username, loginType, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/audit/operation-logs")
    public ApiResponse<PageResponse<SystemVO.AuditLogVO>> operationLogs(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("audit:operation:view");
        return ApiResponse.success(
                systemManagementAppService.listOperationLogs(currentUser, username, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/audit/verification-logs")
    public ApiResponse<PageResponse<SystemVO.AuditLogVO>> verificationLogs(
            @RequestParam(name = "channel", required = false) String channel,
            @RequestParam(name = "scene", required = false) String scene,
            @RequestParam(name = "resultStatus", required = false) String resultStatus,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("audit:operation:view");
        return ApiResponse.success(
                systemManagementAppService.listVerificationLogs(currentUser, channel, scene, resultStatus, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/monitoring/dashboard/summary")
    public ApiResponse<SystemVO.DashboardSummaryVO> dashboardSummary() {
        CurrentUser currentUser = requireTrustedCurrentUser();
        return ApiResponse.success(
                systemManagementAppService.dashboardSummary(currentUser),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/monitoring/online-users")
    public ApiResponse<PageResponse<SystemVO.OnlineSessionVO>> onlineUsers(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("system:online-user:view");
        return ApiResponse.success(
                onlineSessionManagementAppService.listOnlineSessions(currentUser, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping(value = "/monitoring/online-users/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter onlineUserEvents() {
        CurrentUser currentUser = require("system:online-user:view");
        return onlineSessionManagementAppService.stream(currentUser);
    }

    @DeleteMapping("/monitoring/online-users/{sessionId}")
    @RepeatSubmit
    public ApiResponse<Boolean> kickOnlineUser(@PathVariable("sessionId") String sessionId) {
        CurrentUser currentUser = require("system:online-user:kick");
        return ApiResponse.success(
                onlineSessionManagementAppService.kickSession(currentUser, sessionId),
                TraceContext.getRequestId()
        );
    }

    @PatchMapping("/monitoring/online-users/{userId}/ban")
    @RepeatSubmit
    public ApiResponse<Boolean> banOnlineUser(@PathVariable("userId") Long userId) {
        CurrentUser currentUser = require("system:online-user:ban");
        return ApiResponse.success(
                onlineSessionManagementAppService.banUser(currentUser, userId),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        permissionGuard.requirePermission(currentUser, permissionKey);
        return requireTrustedCurrentUser(currentUser);
    }

    private CurrentUser requireTrustedCurrentUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return requireTrustedCurrentUser(currentUser);
    }

    private CurrentUser requireTrustedCurrentUser(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser;
    }

    private <T> T recordPlatformConfigRead(Supplier<T> supplier) {
        long started = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            ownerRuntimeMetrics.recordPlatformConfigRead(Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private <T> T recordPlatformBootstrap(Supplier<T> supplier) {
        long started = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            ownerRuntimeMetrics.recordPlatformBootstrap(Duration.ofNanos(System.nanoTime() - started));
        }
    }
}
