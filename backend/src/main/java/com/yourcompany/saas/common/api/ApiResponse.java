package com.yourcompany.saas.common.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yourcompany.saas.common.enums.ErrorCode;

import java.time.LocalDateTime;

public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private String requestId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    public ApiResponse() {
    }

    public ApiResponse(String code, String message, T data, String requestId, LocalDateTime timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getMessage(),
                data,
                requestId,
                LocalDateTime.now()
        );
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String requestId) {
        return new ApiResponse<>(
                errorCode.getCode(),
                errorCode.getMessage(),
                null,
                requestId,
                LocalDateTime.now()
        );
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String requestId, String message) {
        ApiResponse<T> fail = fail(errorCode, requestId);
        fail.setMessage(message);
        return fail;
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
