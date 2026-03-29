package com.yourcompany.saas.common.exception;

import com.yourcompany.saas.common.enums.ErrorCode;

public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String errorMessage;
    private final String userMessage;

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, message);
    }

    public BizException(ErrorCode errorCode, String errorMessage, String userMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.userMessage = userMessage;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
