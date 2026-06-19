package com.lumira.saas.modules.system.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.api.client.FileInternalApi;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.dto.ProfileDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.api.file.FileObjectDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final SystemManagementAppService systemManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final FileInternalApi fileInternalApi;

    public ProfileController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            FileInternalApi fileInternalApi
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.fileInternalApi = fileInternalApi;
    }

    @GetMapping("/summary")
    public ApiResponse<SystemVO.ProfileSummaryVO> summary() {
        return ApiResponse.success(
                systemManagementAppService.profileSummary(securityContextFacade.getCurrentUser()),
                TraceContext.getRequestId()
        );
    }

    @PutMapping
    @RepeatSubmit
    public ApiResponse<CurrentUserVO> updateBasicInfo(@Valid @RequestBody ProfileDTO.BasicInfoUpdateRequest request) {
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserProfile(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/email")
    @RepeatSubmit
    public ApiResponse<CurrentUserVO> updateEmail(@Valid @RequestBody ProfileDTO.EmailUpdateRequest request) {
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserEmail(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/contact-bind/challenge")
    @RepeatSubmit
    public ApiResponse<SystemVO.VerificationChallengeVO> contactBindChallenge(@Valid @RequestBody ProfileDTO.ContactBindChallengeRequest request) {
        return ApiResponse.success(
                systemManagementAppService.startCurrentUserContactBindChallenge(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/contact-bind")
    @RepeatSubmit
    public ApiResponse<CurrentUserVO> contactBind(@Valid @RequestBody ProfileDTO.ContactBindRequest request) {
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserContactBinding(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/locale")
    @RepeatSubmit
    public ApiResponse<CurrentUserVO> updateLocale(@Valid @RequestBody ProfileDTO.LocaleUpdateRequest request) {
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserLocale(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/password")
    @RepeatSubmit
    public ApiResponse<Boolean> updatePassword(@Valid @RequestBody ProfileDTO.PasswordUpdateRequest request) {
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserPassword(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping(value = "/uploads/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        FileObjectDTO uploaded = fileInternalApi.uploadImage(file, "头像", "个人头像上传");
        return ApiResponse.success(uploaded.publicUrl(), TraceContext.getRequestId());
    }
}
