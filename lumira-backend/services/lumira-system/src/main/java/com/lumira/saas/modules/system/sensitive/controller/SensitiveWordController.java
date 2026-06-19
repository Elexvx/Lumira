package com.lumira.saas.modules.system.sensitive.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordService;
import com.lumira.saas.modules.system.sensitive.dto.SensitiveWordDTO;
import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/sensitive-words")
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public SensitiveWordController(
            SensitiveWordService sensitiveWordService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.sensitiveWordService = sensitiveWordService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ApiResponse<PageResponse<SensitiveWordVO.WordRecord>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("plugin:sensitive-words:view");
        return ApiResponse.success(
                sensitiveWordService.listWords(securityContextFacade.getCurrentUser(), keyword, enabled, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<SensitiveWordVO.WordRecord> detail(@PathVariable("id") Long id) {
        require("plugin:sensitive-words:view");
        return ApiResponse.success(sensitiveWordService.getWord(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<SensitiveWordVO.WordRecord> create(@Valid @RequestBody SensitiveWordDTO.UpsertRequest request) {
        require("plugin:sensitive-words:manage");
        return ApiResponse.success(sensitiveWordService.createWord(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<SensitiveWordVO.WordRecord> update(@PathVariable("id") Long id, @Valid @RequestBody SensitiveWordDTO.UpsertRequest request) {
        require("plugin:sensitive-words:manage");
        return ApiResponse.success(sensitiveWordService.updateWord(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/{id}/status")
    @RepeatSubmit
    public ApiResponse<Boolean> updateStatus(@PathVariable("id") Long id, @RequestBody SensitiveWordDTO.StatusRequest request) {
        require("plugin:sensitive-words:manage");
        return ApiResponse.success(sensitiveWordService.updateStatus(securityContextFacade.getCurrentUser(), id, request.getEnabled()), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
        require("plugin:sensitive-words:manage");
        return ApiResponse.success(sensitiveWordService.deleteWord(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<SensitiveWordVO.ImportResult> importWords(@RequestParam("file") MultipartFile file) {
        require("plugin:sensitive-words:import");
        return ApiResponse.success(sensitiveWordService.importWords(securityContextFacade.getCurrentUser(), file), TraceContext.getRequestId());
    }

    @PostMapping("/check")
    public ApiResponse<SensitiveWordVO.CheckResult> check(@RequestBody SensitiveWordDTO.CheckRequest request) {
        require("plugin:sensitive-words:view");
        return ApiResponse.success(
                sensitiveWordService.checkText(securityContextFacade.getCurrentUser(), request.getText(), request.getFieldPath()),
                TraceContext.getRequestId()
        );
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
