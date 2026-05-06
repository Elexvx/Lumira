package com.legendary.invention.auth.service;

import com.legendary.invention.api.auth.CurrentUserDTO;
import com.legendary.invention.api.auth.LoginCodeChallengeDTO;
import com.legendary.invention.api.auth.LoginRequest;
import com.legendary.invention.api.auth.LoginResponseDTO;
import com.legendary.invention.api.auth.RefreshTokenRequest;
import com.legendary.invention.api.auth.RefreshTokenResponseDTO;
import com.legendary.invention.api.auth.SecondFactorCompleteRequest;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import com.alibaba.csp.sentinel.slots.block.BlockException;

public final class AuthSentinelBlockHandler {

    private AuthSentinelBlockHandler() {
    }

    public static LoginResponseDTO loginBlocked(LoginRequest request, HttpServletRequest httpServletRequest, BlockException throwable) {
        throw trafficLimited("登录请求过于频繁，请稍后再试");
    }

    public static LoginCodeChallengeDTO loginCodeChallengeBlocked(
            com.legendary.invention.api.auth.LoginCodeChallengeRequest request,
            HttpServletRequest httpServletRequest,
            BlockException throwable
    ) {
        throw trafficLimited("验证码登录请求过于频繁，请稍后再试");
    }

    public static LoginResponseDTO completeLoginCodeLoginBlocked(
            com.legendary.invention.api.auth.LoginCodeCompleteRequest request,
            HttpServletRequest httpServletRequest,
            BlockException throwable
    ) {
        throw trafficLimited("验证码登录确认请求过于频繁，请稍后再试");
    }

    public static LoginResponseDTO completeSecondFactorLoginBlocked(
            SecondFactorCompleteRequest request,
            HttpServletRequest httpServletRequest,
            BlockException throwable
    ) {
        throw trafficLimited("二次验证请求过于频繁，请稍后再试");
    }

    public static RefreshTokenResponseDTO refreshTokenBlocked(RefreshTokenRequest request, BlockException throwable) {
        throw trafficLimited("刷新令牌请求过于频繁，请稍后再试");
    }

    public static CurrentUserDTO currentUserBlocked(BlockException throwable) {
        throw trafficLimited("当前用户查询请求过于频繁，请稍后再试");
    }

    private static BizException trafficLimited(String message) {
        return new BizException(ErrorCode.TRAFFIC_LIMITED, message, ErrorCode.TRAFFIC_LIMITED.getDefaultUserMessage());
    }
}
