package com.legendary.invention.saas.modules.system.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import com.alibaba.csp.sentinel.slots.block.BlockException;

public final class PublicCaptchaSentinelBlockHandler {

    private PublicCaptchaSentinelBlockHandler() {
    }

    public static ApiResponse<SystemVO.CaptchaChallengeVO> challengeBlocked(String captchaType, BlockException exception) {
        return blocked();
    }

    public static ApiResponse<SystemVO.CaptchaVerifyVO> verifySliderBlocked(
            SystemDTO.CaptchaSliderVerifyRequest request,
            BlockException exception
    ) {
        return blocked();
    }

    private static <T> ApiResponse<T> blocked() {
        return ApiResponse.fail(
                ErrorCode.TRAFFIC_LIMITED,
                ErrorCode.TRAFFIC_LIMITED.getDefaultMessage(),
                ErrorCode.TRAFFIC_LIMITED.getDefaultUserMessage(),
                TraceContext.getRequestId(),
                null
        );
    }
}
