package com.lumira.common.exception;

import com.lumira.common.enums.ErrorCode;

public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String userMessage;

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, errorCode == null ? null : errorCode.getDefaultUserMessage());
    }

    public BizException(ErrorCode errorCode, String message, String userMessage) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
