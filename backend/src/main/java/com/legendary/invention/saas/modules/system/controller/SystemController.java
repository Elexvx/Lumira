package com.legendary.invention.saas.modules.system.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.api.client.FileInternalApi;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.iam.service.PermissionGuard;
import com.legendary.invention.saas.modules.system.app.SystemManagementAppService;
import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import com.legendary.invention.api.file.FileObjectDTO;
import jakarta.validation.Valid;
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

    public SystemController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileInternalApi fileInternalApi
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.fileInternalApi = fileInternalApi;
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
            @RequestParam(name = "mobile", required = false) String mobile,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("system:user:view");
        return ApiResponse.success(
                systemManagementAppService.listUsers(securityContextFacade.getCurrentUser(), username, mobile, status, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/users/{id}")
    public ApiResponse<SystemVO.UserDetailVO> user(@PathVariable("id") Long id) {
        require("system:user:view");
        return ApiResponse.success(systemManagementAppService.getUser(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/users")
    public ApiResponse<SystemVO.UserDetailVO> createUser(@Valid @RequestBody SystemDTO.UserUpsertRequest request) {
        require("system:user:create");
        return ApiResponse.success(systemManagementAppService.createUser(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/users/{id}")
    public ApiResponse<SystemVO.UserDetailVO> updateUser(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.UserUpsertRequest request) {
        require("system:user:update");
        return ApiResponse.success(systemManagementAppService.updateUser(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<Boolean> changeUserStatus(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.UserStatusRequest request) {
        require("system:user:status");
        return ApiResponse.success(systemManagementAppService.updateUserStatus(securityContextFacade.getCurrentUser(), id, request.getStatus()), TraceContext.getRequestId());
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

    @PostMapping("/roles")
    public ApiResponse<SystemVO.RoleDetailVO> createRole(@Valid @RequestBody SystemDTO.RoleUpsertRequest request) {
        require("system:role:create");
        return ApiResponse.success(systemManagementAppService.createRole(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<SystemVO.RoleDetailVO> updateRole(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.RoleUpsertRequest request) {
        require("system:role:update");
        return ApiResponse.success(systemManagementAppService.updateRole(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PutMapping("/roles/{id}/permissions")
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
    public ApiResponse<SystemVO.MenuVO> createMenu(@Valid @RequestBody SystemDTO.MenuUpsertRequest request) {
        require("system:menu:create");
        return ApiResponse.success(systemManagementAppService.createMenu(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/menus/{id}")
    public ApiResponse<SystemVO.MenuVO> updateMenu(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.MenuUpsertRequest request) {
        require("system:menu:update");
        return ApiResponse.success(systemManagementAppService.updateMenu(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PutMapping("/menus/reorder")
    public ApiResponse<Boolean> reorderMenus(@Valid @RequestBody SystemDTO.MenuReorderRequest request) {
        require("system:menu:update");
        return ApiResponse.success(systemManagementAppService.reorderMenus(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PatchMapping("/menus/{id}/status")
    public ApiResponse<Boolean> updateMenuStatus(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.MenuStatusRequest request) {
        require("system:menu:status");
        return ApiResponse.success(systemManagementAppService.updateMenuStatus(securityContextFacade.getCurrentUser(), id, request.getStatus()), TraceContext.getRequestId());
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
    public ApiResponse<SystemVO.DictTypeVO> createDictType(@Valid @RequestBody SystemDTO.DictTypeUpsertRequest request) {
        require("system:dict:create");
        return ApiResponse.success(systemManagementAppService.createDictType(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/dict-types/{id}")
    public ApiResponse<SystemVO.DictTypeVO> updateDictType(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.DictTypeUpsertRequest request) {
        require("system:dict:update");
        return ApiResponse.success(systemManagementAppService.updateDictType(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @GetMapping("/dict-types/{id}/items")
    public ApiResponse<List<SystemVO.DictItemVO>> dictItems(@PathVariable("id") Long id) {
        require("system:dict:view");
        return ApiResponse.success(systemManagementAppService.listDictItems(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/dict-types/{id}/items")
    public ApiResponse<SystemVO.DictItemVO> createDictItem(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.DictItemUpsertRequest request) {
        require("system:dict:create");
        return ApiResponse.success(systemManagementAppService.createDictItem(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PutMapping("/dict-types/{dictTypeId}/items/{itemId}")
    public ApiResponse<SystemVO.DictItemVO> updateDictItem(
            @PathVariable("dictTypeId") Long dictTypeId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody SystemDTO.DictItemUpsertRequest request
    ) {
        require("system:dict:update");
        return ApiResponse.success(systemManagementAppService.updateDictItem(securityContextFacade.getCurrentUser(), dictTypeId, itemId, request), TraceContext.getRequestId());
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
    public ApiResponse<SystemVO.ConfigVO> createConfig(@Valid @RequestBody SystemDTO.ConfigUpsertRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.createConfig(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/configs/{id}")
    public ApiResponse<SystemVO.ConfigVO> updateConfig(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.ConfigUpsertRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateConfig(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @GetMapping("/profile-field-settings")
    public ApiResponse<List<SystemVO.ProfileFieldSettingVO>> profileFieldSettings() {
        require("system:config:view");
        return ApiResponse.success(systemManagementAppService.getProfileFieldSettings(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PutMapping("/profile-field-settings")
    public ApiResponse<List<SystemVO.ProfileFieldSettingVO>> updateProfileFieldSettings(
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
    public ApiResponse<SystemVO.SmtpSettingsVO> updateSmtpSettings(@Valid @RequestBody SystemDTO.SmtpSettingsRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateSmtpSettings(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping("/smtp-settings/test")
    public ApiResponse<SystemVO.SmtpTestVO> testSmtpSettings(@Valid @RequestBody SystemDTO.SmtpTestRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.testSmtpSettings(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/security-settings")
    public ApiResponse<SystemVO.SecuritySettingsVO> securitySettings() {
        return ApiResponse.success(systemManagementAppService.getSecuritySettings(), TraceContext.getRequestId());
    }

    @PutMapping("/security-settings")
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
    public ApiResponse<SystemVO.WatermarkSettingsVO> updateWatermarkSettings(@RequestBody SystemDTO.WatermarkSettingsRequest request) {
        require("system:config:update");
        return ApiResponse.success(systemManagementAppService.updateWatermarkSettings(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/branding-settings")
    public ApiResponse<SystemVO.BrandingSettingsVO> brandingSettings() {
        return ApiResponse.success(
                systemManagementAppService.getBrandingSettings(securityContextFacade.getCurrentUser()),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/branding-settings")
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
    public ApiResponse<SystemVO.AgreementSettingsVO> updateAgreementSettings(@RequestBody SystemDTO.AgreementSettingsRequest request) {
        require("system:config:update");
        return ApiResponse.success(
                systemManagementAppService.updateAgreementSettings(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping(value = "/uploads/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file) {
        require("system:config:update");
        FileObjectDTO uploaded = fileInternalApi.uploadImage(file, "系统图片", "系统配置图片上传");
        return ApiResponse.success(uploaded.publicUrl(), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
