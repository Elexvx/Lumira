package com.legendary.invention.saas.modules.system.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import com.alibaba.csp.sentinel.slots.block.BlockException;

public final class PublicSystemSentinelBlockHandler {

    private PublicSystemSentinelBlockHandler() {
    }

    public static ApiResponse<SystemVO.BrandingSettingsVO> brandingSettingsBlocked(BlockException exception) {
        return blocked();
    }

    public static ApiResponse<SystemVO.AgreementSettingsVO> agreementSettingsBlocked(BlockException exception) {
        return blocked();
    }

    public static ApiResponse<SystemVO.SecuritySettingsVO> securitySettingsBlocked(BlockException exception) {
        return blocked();
    }

    public static ApiResponse<SystemVO.LoginCapabilitiesVO> loginCapabilitiesBlocked(BlockException exception) {
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
