package com.yourcompany.saas.common.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yourcompany.saas.common.enums.ErrorCode;

import java.time.LocalDateTime;

public class ApiResponse<T> {

    private Integer httpStatus;
    private String code;
    private String message;
    private String errorCode;
    private String errorMessage;
    private String userTip;
    private T data;
    private String requestId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    public ApiResponse() {
    }

    public ApiResponse(
            Integer httpStatus,
            String code,
            String message,
            String errorCode,
            String errorMessage,
            String userTip,
            T data,
            String requestId,
            LocalDateTime timestamp
    ) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.userTip = userTip;
        this.data = data;
        this.requestId = requestId;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(
                200,
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getDefaultMessage(),
                null,
                null,
                null,
                data,
                requestId,
                LocalDateTime.now()
        );
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String requestId) {
        return new ApiResponse<>(
                errorCode.getHttpStatus(),
                errorCode.getCode(),
                errorCode.getDefaultMessage(),
                errorCode.getCode(),
                errorCode.getDefaultMessage(),
                errorCode.getDefaultUserTip(),
                null,
                requestId,
                LocalDateTime.now()
        );
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String requestId, String message) {
        ApiResponse<T> fail = fail(errorCode, requestId);
        fail.setMessage(message);
        fail.setErrorMessage(message);
        return fail;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getUserTip() {
        return userTip;
    }

    public void setUserTip(String userTip) {
        this.userTip = userTip;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
