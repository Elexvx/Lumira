package com.lumira.saas.modules.system.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.infrastructure.security.service.CaptchaService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/captcha")
public class PublicCaptchaController {

    private final CaptchaService captchaService;

    public PublicCaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }
    @GetMapping("/challenge")
    public ApiResponse<SystemVO.CaptchaChallengeVO> challenge(@RequestParam(name = "captchaType", defaultValue = "IMAGE") String captchaType) {
        return ApiResponse.success(captchaService.createChallenge(captchaType), TraceContext.getRequestId());
    }
    @PostMapping("/slider/verify")
    @RepeatSubmit
    public ApiResponse<SystemVO.CaptchaVerifyVO> verifySlider(@Valid @RequestBody SystemDTO.CaptchaSliderVerifyRequest request) {
        return ApiResponse.success(captchaService.verifySliderChallenge(request), TraceContext.getRequestId());
    }
}
