package com.lumira.common.web.security.ratelimit;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;

public class RateLimitExceededException extends BizException {

    public RateLimitExceededException() {
        super(ErrorCode.TRAFFIC_LIMITED, "Request rate limited", "当前访问过于频繁，请稍后再试");
    }
}
