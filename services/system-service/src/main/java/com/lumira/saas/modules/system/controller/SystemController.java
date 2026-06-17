package com.lumira.saas.modules.system.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.api.client.FileInternalApi;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.export.ExportDTO;
import com.lumira.saas.modules.system.export.ExportFieldVO;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.export.ExportVO;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import com.lumira.saas.modules.system.user.app.UserExportAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.api.file.FileObjectDTO;
import jakarta.validation.Valid;
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
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemManagementAppService systemManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final FileInternalApi fileInternalApi;
    private final UserExportAppService userExportAppService;
    private final ExportTaskService exportTaskService;

    public SystemController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileInternalApi fileInternalApi,
            UserExportAppService userExportAppService,
            ExportTaskService exportTaskService
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.fileInternalApi = fileInternalApi;
        this.userExportAppService = userExportAppService;
        this.exportTaskService = exportTaskService;
    }

    @GetMapping("/permissions")
    public ApiResponse<List<SystemVO.PermissionVO>> permissions() {
        require("system:role:view");
        return ApiResponse.success(systemManagementAppService.listPermissions(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/permissions/tree")
    public ApiResponse<List<SystemVO.PermissionTreeVO>> permissionTree() {
        require("system:role:view");
        return ApiResponse.success(systemManagementAppService.listPermissionTree(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<SystemVO.UserVO>> users(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "userId", required = false) Long userId,
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
        require("system:user:view");
        return ApiResponse.success(
                systemManagementAppService.listUsers(
                        securityContextFacade.getCurrentUser(),
                        userId,
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
        require("system:user:view");
        return ApiResponse.success(systemManagementAppService.getUser(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/users/export-fields")
    public ApiResponse<List<ExportFieldVO>> userExportFields() {
        require("system:user:export");
        return ApiResponse.success(userExportAppService.listUserExportFields(), TraceContext.getRequestId());
    }

    @PostMapping("/users/export")
    public ApiResponse<ExportVO.ExportStartVO> exportUsers(@Valid @RequestBody ExportDTO.UserExportRequest request) {
        require("system:user:export");
        return ApiResponse.success(userExportAppService.exportUsers(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/export-tasks/{taskId}")
    public ApiResponse<ExportVO.ExportTaskVO> exportTask(@PathVariable("taskId") Long taskId) {
        require("system:user:export");
        return ApiResponse.success(exportTaskService.getTask(securityContextFacade.getCurrentUser(), taskId), TraceContext.getRequestId());
    }

    @PostMapping("/users")
    @RepeatSubmit
    public ApiResponse<SystemVO.UserDetailVO> createUser(@Valid @RequestBody SystemDTO.UserUpsertRequest request) {
        require("system:user:create");
        return ApiResponse.success(systemManagementAppService.createUser(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/users/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.UserDetailVO> updateUser(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.UserUpsertRequest request) {
        require("system:user:update");
        return ApiResponse.success(systemManagementAppService.updateUser(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/users/{id}/status")
    @RepeatSubmit
    public ApiResponse<Boolean> changeUserStatus(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.UserStatusRequest request) {
        require("system:user:status");
        return ApiResponse.success(systemManagementAppService.updateUserStatus(securityContextFacade.getCurrentUser(), id, request.getStatus()), TraceContext.getRequestId());
    }

    @DeleteMapping("/users/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteUser(@PathVariable("id") Long id) {
        require("system:user:delete");
        return ApiResponse.success(systemManagementAppService.deleteUser(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/users/{id}/roles")
    public ApiResponse<List<SystemVO.RoleVO>> userRoles(@PathVariable("id") Long id) {
        require("system:user:view");
        return ApiResponse.success(systemManagementAppService.listUserRoles(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/roles")
    public ApiResponse<PageResponse<SystemVO.RoleVO>> roles(
            @RequestParam(name = "roleCode", required = false) String roleCode,
            @RequestParam(name = "roleName", required = false) String roleName,
            @RequestParam(name = "roleType", required = false) String roleType,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("system:role:view");
        return ApiResponse.success(
                systemManagementAppService.listRoles(securityContextFacade.getCurrentUser(), roleCode, roleName, roleType, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/roles/{id}")
    public ApiResponse<SystemVO.RoleDetailVO> role(@PathVariable("id") Long id) {
        require("system:role:view");
        return ApiResponse.success(systemManagementAppService.getRole(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/roles/default-registration-role")
    public ApiResponse<SystemVO.DefaultRegistrationRoleVO> defaultRegistrationRole() {
        require("system:role:view");
        return ApiResponse.success(systemManagementAppService.getDefaultRegistrationRole(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PutMapping("/roles/default-registration-role")
    @RepeatSubmit
    public ApiResponse<SystemVO.DefaultRegistrationRoleVO> updateDefaultRegistrationRole(@Valid @RequestBody SystemDTO.DefaultRegistrationRoleRequest request) {
        require("system:role:update");
        return ApiResponse.success(systemManagementAppService.updateDefaultRegistrationRole(securityContextFacade.getCurrentUser(), request.getRoleId()), TraceContext.getRequestId());
    }

    @PostMapping("/roles")
    @RepeatSubmit
    public ApiResponse<SystemVO.RoleDetailVO> createRole(@Valid @RequestBody SystemDTO.RoleUpsertRequest request) {
        require("system:role:create");
        return ApiResponse.success(systemManagementAppService.createRole(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/roles/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.RoleDetailVO> updateRole(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.RoleUpsertRequest request) {
        require("system:role:update");
        return ApiResponse.success(systemManagementAppService.updateRole(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/roles/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteRole(@PathVariable("id") Long id) {
        require("system:role:delete");
        return ApiResponse.success(systemManagementAppService.deleteRole(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PutMapping("/roles/{id}/permissions")
    @RepeatSubmit
    public ApiResponse<Boolean> updateRolePermissions(@PathVariable("id") Long id, @RequestBody SystemDTO.RolePermissionRequest request) {
        require("system:role:permissions");
        return ApiResponse.success(systemManagementAppService.updateRolePermissions(securityContextFacade.getCurrentUser(), id, request.getPermissionKeys()), TraceContext.getRequestId());
    }

    @GetMapping("/menus")
    public ApiResponse<List<SystemVO.MenuVO>> menus() {
        require("system:menu:view");
        return ApiResponse.success(systemManagementAppService.listMenus(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/menus/{id}")
    public ApiResponse<SystemVO.MenuVO> menu(@PathVariable("id") Long id) {
        require("system:menu:view");
        return ApiResponse.success(systemManagementAppService.getMenu(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/menus")
    @RepeatSubmit
    public ApiResponse<SystemVO.MenuVO> createMenu(@Valid @RequestBody SystemDTO.MenuUpsertRequest request) {
        require("system:menu:create");
        return ApiResponse.success(systemManagementAppService.createMenu(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/menus/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.MenuVO> updateMenu(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.MenuUpsertRequest request) {
        require("system:menu:update");
        return ApiResponse.success(systemManagementAppService.updateMenu(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PutMapping("/menus/reorder")
    @RepeatSubmit
    public ApiResponse<Boolean> reorderMenus(@Valid @RequestBody SystemDTO.MenuReorderRequest request) {
        require("system:menu:update");
        return ApiResponse.success(systemManagementAppService.reorderMenus(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PatchMapping("/menus/{id}/status")
    @RepeatSubmit
    public ApiResponse<Boolean> updateMenuStatus(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.MenuStatusRequest request) {
        require("system:menu:status");
        return ApiResponse.success(systemManagementAppService.updateMenuStatus(securityContextFacade.getCurrentUser(), id, request.getStatus()), TraceContext.getRequestId());
    }

    @DeleteMapping("/menus/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteMenu(@PathVariable("id") Long id) {
        require("system:menu:delete");
        return ApiResponse.success(systemManagementAppService.deleteMenu(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/dict-types")
    public ApiResponse<PageResponse<SystemVO.DictTypeVO>> dictTypes(
            @RequestParam(name = "dictCode", required = false) String dictCode,
            @RequestParam(name = "dictName", required = false) String dictName,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("system:dict:view");
        return ApiResponse.success(
                systemManagementAppService.listDictTypes(securityContextFacade.getCurrentUser(), dictCode, dictName, status, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/dict-types/{id}")
    public ApiResponse<SystemVO.DictTypeVO> dictType(@PathVariable("id") Long id) {
        require("system:dict:view");
        return ApiResponse.success(systemManagementAppService.getDictType(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/dict-types")
    @RepeatSubmit
    public ApiResponse<SystemVO.DictTypeVO> createDictType(@Valid @RequestBody SystemDTO.DictTypeUpsertRequest request) {
        require("system:dict:create");
        return ApiResponse.success(systemManagementAppService.createDictType(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/dict-types/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.DictTypeVO> updateDictType(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.DictTypeUpsertRequest request) {
        require("system:dict:update");
        return ApiResponse.success(systemManagementAppService.updateDictType(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/dict-types/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteDictType(@PathVariable("id") Long id) {
        require("system:dict:delete");
        return ApiResponse.success(systemManagementAppService.deleteDictType(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/dict-types/{id}/items")
    public ApiResponse<List<SystemVO.DictItemVO>> dictItems(@PathVariable("id") Long id) {
        require("system:dict:view");
        return ApiResponse.success(systemManagementAppService.listDictItems(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/dict-types/{id}/items")
    @RepeatSubmit
    public ApiResponse<SystemVO.DictItemVO> createDictItem(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.DictItemUpsertRequest request) {
        require("system:dict:create");
        return ApiResponse.success(systemManagementAppService.createDictItem(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PutMapping("/dict-types/{dictTypeId}/items/{itemId}")
    @RepeatSubmit
    public ApiResponse<SystemVO.DictItemVO> updateDictItem(
            @PathVariable("dictTypeId") Long dictTypeId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody SystemDTO.DictItemUpsertRequest request
    ) {
        require("system:dict:update");
        return ApiResponse.success(systemManagementAppService.updateDictItem(securityContextFacade.getCurrentUser(), dictTypeId, itemId, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/dict-types/{dictTypeId}/items/{itemId}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteDictItem(
            @PathVariable("dictTypeId") Long dictTypeId,
            @PathVariable("itemId") Long itemId
    ) {
        require("system:dict:delete");
        return ApiResponse.success(systemManagementAppService.deleteDictItem(securityContextFacade.getCurrentUser(), dictTypeId, itemId), TraceContext.getRequestId());
    }

    @GetMapping("/configs")
    public ApiResponse<PageResponse<SystemVO.ConfigVO>> configs(
            @RequestParam(name = "configKey", required = false) String configKey,
            @RequestParam(name = "configName", required = false) String configName,
            @RequestParam(name = "configScope", required = false) String configScope,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("system:config:view");
        return ApiResponse.success(
                systemManagementAppService.listConfigs(securityContextFacade.getCurrentUser(), configKey, configName, configScope, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/configs/{id}")
    public ApiResponse<SystemVO.ConfigVO> config(@PathVariable("id") Long id) {
        require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getConfig(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/configs")
    @RepeatSubmit
    public ApiResponse<SystemVO.ConfigVO> createConfig(@Valid @RequestBody SystemDTO.ConfigUpsertRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.createConfig(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/configs/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.ConfigVO> updateConfig(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.ConfigUpsertRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateConfig(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @GetMapping("/profile-field-settings")
    public ApiResponse<List<ProfileFieldSettingVO>> profileFieldSettings() {
        require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getProfileFieldSettings(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PutMapping("/profile-field-settings")
    @RepeatSubmit
    public ApiResponse<List<ProfileFieldSettingVO>> updateProfileFieldSettings(
            @Valid @RequestBody SystemDTO.ProfileFieldSettingsRequest request
    ) {
        require("system:config:update");
        return ApiResponse.success(
                systemManagementAppService.updateProfileFieldSettings(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/smtp-settings")
    public ApiResponse<SystemVO.SmtpSettingsVO> smtpSettings() {
        require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getSmtpSettings(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PutMapping("/smtp-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmtpSettingsVO> updateSmtpSettings(@Valid @RequestBody SystemDTO.SmtpSettingsRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateSmtpSettings(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @DeleteMapping("/smtp-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmtpSettingsVO> resetSmtpSettings() {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.resetSmtpSettings(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/notification/wechat-official-settings")
    public ApiResponse<SystemVO.WechatOfficialAccountSettingsVO> wechatOfficialAccountSettings() {
        require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getWechatOfficialAccountSettings(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PutMapping("/notification/wechat-official-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.WechatOfficialAccountSettingsVO> updateWechatOfficialAccountSettings(@Valid @RequestBody SystemDTO.WechatOfficialAccountSettingsRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateWechatOfficialAccountSettings(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping("/smtp-settings/test")
    @RepeatSubmit
    public ApiResponse<SystemVO.SmtpTestVO> testSmtpSettings(@Valid @RequestBody SystemDTO.SmtpTestRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.testSmtpSettings(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/security-settings")
    public ApiResponse<SystemVO.SecuritySettingsVO> securitySettings() {
        return ApiResponse.success(systemManagementAppService.getSecuritySettings(), TraceContext.getRequestId());
    }

    @GetMapping("/runtime-appearance-settings")
    public ApiResponse<SystemVO.RuntimeAppearanceSettingsVO> runtimeAppearanceSettings() {
        return ApiResponse.success(
                systemManagementAppService.getRuntimeAppearanceSettings(securityContextFacade.getCurrentUser()),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/security-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.SecuritySettingsVO> updateSecuritySettings(@Valid @RequestBody SystemDTO.SecuritySettingsRequest request) {
        require("system:config:update");
        return ApiResponse.success(
                systemManagementAppService.updateSecuritySettings(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }


    @GetMapping("/watermark-settings")
    public ApiResponse<SystemVO.WatermarkSettingsVO> watermarkSettings() {
        require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getWatermarkSettings(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PutMapping("/watermark-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.WatermarkSettingsVO> updateWatermarkSettings(@RequestBody SystemDTO.WatermarkSettingsRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateWatermarkSettings(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/floating-window-settings")
    public ApiResponse<SystemVO.FloatingWindowSettingsVO> floatingWindowSettings() {
        return ApiResponse.success(systemManagementAppService.getFloatingWindowSettings(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PutMapping("/floating-window-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.FloatingWindowSettingsVO> updateFloatingWindowSettings(@RequestBody SystemDTO.FloatingWindowSettingsRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateFloatingWindowSettings(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/branding-settings")
    public ApiResponse<SystemVO.BrandingSettingsVO> brandingSettings() {
        return ApiResponse.success(
                systemManagementAppService.getBrandingSettings(securityContextFacade.getCurrentUser()),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/branding-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.BrandingSettingsVO> updateBrandingSettings(@RequestBody SystemDTO.BrandingSettingsRequest request) {
        require("system:config:update");
        return ApiResponse.success(
                systemManagementAppService.updateBrandingSettings(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/agreement-settings")
    public ApiResponse<SystemVO.AgreementSettingsVO> agreementSettings() {
        return ApiResponse.success(systemManagementAppService.getAgreementSettings(), TraceContext.getRequestId());
    }

    @PutMapping("/agreement-settings")
    @RepeatSubmit
    public ApiResponse<SystemVO.AgreementSettingsVO> updateAgreementSettings(@RequestBody SystemDTO.AgreementSettingsRequest request) {
        require("system:config:update");
        return ApiResponse.success(
                systemManagementAppService.updateAgreementSettings(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping(value = "/uploads/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file) {
        require("system:config:update");
        FileObjectDTO uploaded = fileInternalApi.uploadImage(file, "系统图片", "系统配置图片上传");
        return ApiResponse.success(uploaded.publicUrl(), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
