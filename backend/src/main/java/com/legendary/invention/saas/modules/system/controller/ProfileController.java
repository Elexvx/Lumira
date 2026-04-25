package com.legendary.invention.saas.modules.system.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.infrastructure.upload.ImageUploadService;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.auth.vo.CurrentUserVO;
import com.legendary.invention.saas.modules.system.app.SystemManagementAppService;
import com.legendary.invention.saas.modules.system.dto.ProfileDTO;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
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
    private final ImageUploadService imageUploadService;

    public ProfileController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            ImageUploadService imageUploadService
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.imageUploadService = imageUploadService;
    }

    @GetMapping("/summary")
    public ApiResponse<SystemVO.ProfileSummaryVO> summary() {
        return ApiResponse.success(
                systemManagementAppService.profileSummary(securityContextFacade.getCurrentUser()),
                TraceContext.getRequestId()
        );
    }

    @PutMapping
    public ApiResponse<CurrentUserVO> updateBasicInfo(@Valid @RequestBody ProfileDTO.BasicInfoUpdateRequest request) {
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserProfile(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/email")
    public ApiResponse<CurrentUserVO> updateEmail(@Valid @RequestBody ProfileDTO.EmailUpdateRequest request) {
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserEmail(securityContextFacade.getCurrentUser(), request.getEmail()),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/contact-bind/challenge")
    public ApiResponse<SystemVO.VerificationChallengeVO> contactBindChallenge(@Valid @RequestBody ProfileDTO.ContactBindChallengeRequest request) {
        return ApiResponse.success(
                systemManagementAppService.startCurrentUserContactBindChallenge(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/contact-bind")
    public ApiResponse<CurrentUserVO> contactBind(@Valid @RequestBody ProfileDTO.ContactBindRequest request) {
        return ApiResponse.success(
                systemManagementAppService.updateCurrentUserContactBinding(securityContextFacade.getCurrentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping(value = "/uploads/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(imageUploadService.upload(file), TraceContext.getRequestId());
    }
}
