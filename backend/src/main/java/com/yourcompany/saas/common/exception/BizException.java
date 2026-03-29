package com.yourcompany.saas.common.exception;

import com.yourcompany.saas.common.enums.ErrorCode;

public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Integer httpStatus;
    private final String userTip;

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, errorCode.getHttpStatus(), message, errorCode.getDefaultUserTip());
    }

    public BizException(ErrorCode errorCode, String message, String userTip) {
        this(errorCode, errorCode.getHttpStatus(), message, userTip);
    }

    public BizException(ErrorCode errorCode, Integer httpStatus, String message, String userTip) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.userTip = userTip;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getUserTip() {
        return userTip;
    }
}
