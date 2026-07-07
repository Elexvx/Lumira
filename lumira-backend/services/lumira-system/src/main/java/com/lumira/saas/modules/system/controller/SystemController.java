package com.lumira.saas.modules.system.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
import com.lumira.saas.modules.system.export.ExportDTO;
import com.lumira.saas.modules.system.export.ExportFieldVO;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.export.ExportVO;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import com.lumira.saas.modules.system.user.app.UserExportAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.api.file.FileObjectDTO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private static final String PUBLIC_BRANDING_UPLOAD_BUCKET = "local";
    private static final String STATUS_ENABLED = "ENABLED";

    private final SystemManagementAppService systemManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final FileInternalApi fileInternalApi;
    private final UserExportAppService userExportAppService;
    private final ExportTaskService exportTaskService;
    private final DictRuntimeService dictRuntimeService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public SystemController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileInternalApi fileInternalApi,
            UserExportAppService userExportAppService,
            ExportTaskService exportTaskService,
            DictRuntimeService dictRuntimeService
    ) {
        this(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                fileInternalApi,
                userExportAppService,
                exportTaskService,
                dictRuntimeService,
                null,
                null,
                null,
                false
        );
    }

    public SystemController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileInternalApi fileInternalApi,
            UserExportAppService userExportAppService,
            ExportTaskService exportTaskService,
            DictRuntimeService dictRuntimeService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                fileInternalApi,
                userExportAppService,
                exportTaskService,
                dictRuntimeService,
                permissionSnapshotService,
                null,
                null,
                false
        );
    }

    public SystemController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileInternalApi fileInternalApi,
            UserExportAppService userExportAppService,
            ExportTaskService exportTaskService,
            DictRuntimeService dictRuntimeService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                fileInternalApi,
                userExportAppService,
                exportTaskService,
                dictRuntimeService,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                false
        );
    }

    @Autowired
    public SystemController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileInternalApi fileInternalApi,
            UserExportAppService userExportAppService,
            ExportTaskService exportTaskService,
            DictRuntimeService dictRuntimeService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                fileInternalApi,
                userExportAppService,
                exportTaskService,
                dictRuntimeService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private SystemController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileInternalApi fileInternalApi,
            UserExportAppService userExportAppService,
            ExportTaskService exportTaskService,
            DictRuntimeService dictRuntimeService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.fileInternalApi = fileInternalApi;
        this.userExportAppService = userExportAppService;
        this.exportTaskService = exportTaskService;
        this.dictRuntimeService = dictRuntimeService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/permissions")
    public ApiResponse<List<SystemVO.PermissionVO>> permissions() {
        CurrentUser currentUser = require("system:role:view");
        return ApiResponse.success(systemManagementAppService.listPermissions(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/permissions/tree")
    public ApiResponse<List<SystemVO.PermissionTreeVO>> permissionTree() {
        CurrentUser currentUser = require("system:role:view");
        return ApiResponse.success(systemManagementAppService.listPermissionTree(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<SystemVO.UserVO>> users(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "uid", required = false) String uid,
            @RequestParam(name = "mobile", required = false) String mobile,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "deptId", required = false) Long deptId,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "registeredStart", required = false) String registeredStart,
            @RequestParam(name = "registeredEnd", required = false) String registeredEnd,
            @RequestParam(name = "lastLoginStart", required = false) String lastLoginStart,
            @RequestParam(name = "lastLoginEnd", required = false) String lastLoginEnd,
            @RequestParam(name = "cursorId", required = false) Long cursorId,
            @RequestParam(name = "cursorCreatedAt", required = false) String cursorCreatedAt,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("system:user:view");
        return ApiResponse.success(
                systemManagementAppService.listUsers(
                        currentUser,
                        userId,
                        uid,
                        username,
                        mobile,
                        email,
                        deptId,
                        status,
                        source,
                        registeredStart,
                        registeredEnd,
                        lastLoginStart,
                        lastLoginEnd,
                        cursorId,
                        cursorCreatedAt,
                        pageNo,
                        pageSize
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/users/{id}")
    public ApiResponse<SystemVO.UserDetailVO> user(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:user:view");
        return ApiResponse.success(systemManagementAppService.getUser(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/users/export-fields")
    public ApiResponse<List<ExportFieldVO>> userExportFields() {
        CurrentUser currentUser = require("system:user:export");
        return ApiResponse.success(userExportAppService.listUserExportFields(currentUser), TraceContext.getRequestId());
    }

    @PostMapping("/users/export")
    public ApiResponse<ExportVO.ExportStartVO> exportUsers(@Valid @RequestBody ExportDTO.UserExportRequest request) {
        CurrentUser currentUser = require("system:user:export");
        return ApiResponse.success(userExportAppService.exportUsers(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/export-tasks/{taskId}")
    public ApiResponse<ExportVO.ExportTaskVO> exportTask(@PathVariable("taskId") Long taskId) {
        CurrentUser currentUser = require("system:user:export");
        return ApiResponse.success(exportTaskService.getTask(currentUser, taskId), TraceContext.getRequestId());
    }

    @PostMapping("/users")
    @RepeatSubmit
    public ApiResponse<SystemVO.UserDetailVO> createUser(@Valid @RequestBody SystemDTO.UserUpsertRequest request) {
        CurrentUser currentUser = require("system:user:create");
        return ApiResponse.success(systemManagementAppService.createUser(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/users/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.UserDetailVO> updateUser(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.UserUpsertRequest request) {
        CurrentUser currentUser = require("system:user:update");
        return ApiResponse.success(systemManagementAppService.updateUser(currentUser, id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/users/{id}/status")
    @RepeatSubmit
    public ApiResponse<Boolean> changeUserStatus(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.UserStatusRequest request) {
        CurrentUser currentUser = require("system:user:status");
        return ApiResponse.success(systemManagementAppService.updateUserStatus(currentUser, id, request.getStatus()), TraceContext.getRequestId());
    }

    @DeleteMapping("/users/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteUser(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:user:delete");
        return ApiResponse.success(systemManagementAppService.deleteUser(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/users/{id}/roles")
    public ApiResponse<List<SystemVO.RoleVO>> userRoles(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:user:view");
        return ApiResponse.success(systemManagementAppService.listUserRoles(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/roles")
    public ApiResponse<PageResponse<SystemVO.RoleVO>> roles(
            @RequestParam(name = "roleCode", required = false) String roleCode,
            @RequestParam(name = "roleName", required = false) String roleName,
            @RequestParam(name = "roleType", required = false) String roleType,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("system:role:view");
        return ApiResponse.success(
                systemManagementAppService.listRoles(currentUser, roleCode, roleName, roleType, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/roles/{id}")
    public ApiResponse<SystemVO.RoleDetailVO> role(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:role:view");
        return ApiResponse.success(systemManagementAppService.getRole(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/roles/default-registration-role")
    public ApiResponse<SystemVO.DefaultRegistrationRoleVO> defaultRegistrationRole() {
        CurrentUser currentUser = require("system:role:view");
        return ApiResponse.success(systemManagementAppService.getDefaultRegistrationRole(currentUser), TraceContext.getRequestId());
    }

    @PutMapping("/roles/default-registration-role")
    @RepeatSubmit
    public ApiResponse<SystemVO.DefaultRegistrationRoleVO> updateDefaultRegistrationRole(@Valid @RequestBody SystemDTO.DefaultRegistrationRoleRequest request) {
        CurrentUser currentUser = require("system:role:update");
        return ApiResponse.success(systemManagementAppService.updateDefaultRegistrationRole(currentUser, request.getRoleId()), TraceContext.getRequestId());
    }

    @PostMapping("/roles")
    @RepeatSubmit
    public ApiResponse<SystemVO.RoleDetailVO> createRole(@Valid @RequestBody SystemDTO.RoleUpsertRequest request) {
        CurrentUser currentUser = require("system:role:create");
        return ApiResponse.success(systemManagementAppService.createRole(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/roles/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.RoleDetailVO> updateRole(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.RoleUpsertRequest request) {
        CurrentUser currentUser = require("system:role:update");
        return ApiResponse.success(systemManagementAppService.updateRole(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/roles/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteRole(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:role:delete");
        return ApiResponse.success(systemManagementAppService.deleteRole(currentUser, id), TraceContext.getRequestId());
    }

    @PutMapping("/roles/{id}/permissions")
    @RepeatSubmit
    public ApiResponse<Boolean> updateRolePermissions(@PathVariable("id") Long id, @RequestBody SystemDTO.RolePermissionRequest request) {
        CurrentUser currentUser = require("system:role:grant");
        return ApiResponse.success(systemManagementAppService.updateRolePermissions(currentUser, id, request.getPermissionKeys()), TraceContext.getRequestId());
    }

    @GetMapping("/menus")
    public ApiResponse<List<SystemVO.MenuVO>> menus() {
        CurrentUser currentUser = require("system:menu:view");
        return ApiResponse.success(systemManagementAppService.listMenus(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/menus/{id}")
    public ApiResponse<SystemVO.MenuVO> menu(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:menu:view");
        return ApiResponse.success(systemManagementAppService.getMenu(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/menus")
    @RepeatSubmit
    public ApiResponse<SystemVO.MenuVO> createMenu(@Valid @RequestBody SystemDTO.MenuUpsertRequest request) {
        CurrentUser currentUser = require("system:menu:create");
        return ApiResponse.success(systemManagementAppService.createMenu(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/menus/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.MenuVO> updateMenu(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.MenuUpsertRequest request) {
        CurrentUser currentUser = require("system:menu:update");
        return ApiResponse.success(systemManagementAppService.updateMenu(currentUser, id, request), TraceContext.getRequestId());
    }

    @PutMapping("/menus/reorder")
    @RepeatSubmit
    public ApiResponse<Boolean> reorderMenus(@Valid @RequestBody SystemDTO.MenuReorderRequest request) {
        CurrentUser currentUser = require("system:menu:update");
        return ApiResponse.success(systemManagementAppService.reorderMenus(currentUser, request), TraceContext.getRequestId());
    }

    @PatchMapping("/menus/{id}/status")
    @RepeatSubmit
    public ApiResponse<Boolean> updateMenuStatus(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.MenuStatusRequest request) {
        CurrentUser currentUser = require("system:menu:status");
        return ApiResponse.success(systemManagementAppService.updateMenuStatus(currentUser, id, request.getStatus()), TraceContext.getRequestId());
    }

    @DeleteMapping("/menus/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteMenu(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:menu:delete");
        return ApiResponse.success(systemManagementAppService.deleteMenu(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/dict-items")
    public ApiResponse<List<SystemVO.DictItemVO>> dictItemsByCode(@RequestParam("dictCode") String dictCode) {
        return ApiResponse.success(
                dictRuntimeService.listEnabledItems(dictCode),
                TraceContext.getRequestId()
        );
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

    @GetMapping("/configs")
    public ApiResponse<PageResponse<SystemVO.ConfigVO>> configs(
            @RequestParam(name = "configKey", required = false) String configKey,
            @RequestParam(name = "configName", required = false) String configName,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(
                systemManagementAppService.listConfigs(currentUser, configKey, configName, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/configs/{id}")
    public ApiResponse<SystemVO.ConfigVO> config(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getConfig(currentUser, id), TraceContext.getRequestId());
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

    @GetMapping("/profile-field-settings")
    public ApiResponse<List<ProfileFieldSettingVO>> profileFieldSettings(
            @RequestParam(name = "pageKey", required = false) String pageKey
    ) {
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getProfileFieldSettings(currentUser, pageKey), TraceContext.getRequestId());
    }

    @PutMapping("/profile-field-settings")
    @RepeatSubmit
    public ApiResponse<List<ProfileFieldSettingVO>> updateProfileFieldSettings(
            @RequestParam(name = "pageKey", required = false) String pageKey,
            @Valid @RequestBody SystemDTO.ProfileFieldSettingsRequest request
    ) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(
                systemManagementAppService.updateProfileFieldSettings(currentUser, request, pageKey),
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

    @PostMapping("/smtp-settings/test")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmtpTestVO> testSmtpSettings(@Valid @RequestBody SystemDTO.SmtpTestRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(systemManagementAppService.testSmtpSettings(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/security-settings")
    public ApiResponse<SystemVO.SecuritySettingsVO> securitySettings() {
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getSecuritySettings(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/runtime-appearance-settings")
    public ApiResponse<SystemVO.RuntimeAppearanceSettingsVO> runtimeAppearanceSettings() {
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(
                systemManagementAppService.getRuntimeAppearanceSettings(currentUser),
                TraceContext.getRequestId()
        );
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
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getFloatingWindowSettings(currentUser), TraceContext.getRequestId());
    }

    @PutMapping("/floating-window-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.FloatingWindowSettingsVO> updateFloatingWindowSettings(@RequestBody SystemDTO.FloatingWindowSettingsRequest request) {
        CurrentUser currentUser = require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateFloatingWindowSettings(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/branding-settings")
    public ApiResponse<SystemVO.BrandingSettingsVO> brandingSettings() {
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(
                systemManagementAppService.getBrandingSettings(currentUser),
                TraceContext.getRequestId()
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
        CurrentUser currentUser = require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getAgreementSettings(currentUser), TraceContext.getRequestId());
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

    @PostMapping(value = "/uploads/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file) {
        CurrentUser currentUser = require("system:config:update");
        FileObjectDTO uploaded = fileInternalApi.uploadImageForUser(
                file,
                "系统图片",
                "系统配置图片上传",
                PUBLIC_BRANDING_UPLOAD_BUCKET,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                currentUser.getSimulatedRoleId()
        );
        return ApiResponse.success(uploaded.publicUrl(), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = requireTrustedUser(currentUser);
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            String currentUserUuid = userSnapshot == null || !StringUtils.hasText(userSnapshot.userUuid())
                    ? null
                    : userSnapshot.userUuid().trim();
            if (userSnapshot == null
                    || userSnapshot.userId() == null
                    || !userId.equals(userSnapshot.userId())
                    || !StringUtils.hasText(currentUserUuid)
                    || !normalizedUserUuid.equals(currentUserUuid)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(currentUserUuid);
            currentUser.setUsername(currentUsername);
            normalizedUserUuid = currentUserUuid;
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        if (authenticatedAccess == null || !isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return authenticatedAccess.currentUser();
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }
}
