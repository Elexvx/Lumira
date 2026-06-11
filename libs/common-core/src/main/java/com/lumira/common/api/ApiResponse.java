package com.lumira.common.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lumira.common.enums.ErrorCode;

import java.time.LocalDateTime;

public class ApiResponse<T> {

    private Integer httpStatus;
    private String code;
    private String message;
    private String userMessage;
    private T data;
    private String requestId;
    private String path;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    public ApiResponse() {
    }

    public ApiResponse(Integer httpStatus, String code, String message, String userMessage, T data, String requestId, LocalDateTime timestamp, String path) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
        this.userMessage = userMessage;
        this.data = data;
        this.requestId = requestId;
        this.timestamp = timestamp;
        this.path = path;
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return success(data, requestId, null);
    }

    public static <T> ApiResponse<T> success(T data, String requestId, String path) {
        return new ApiResponse<>(
                200,
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getDefaultMessage(),
                null,
                data,
                requestId,
                LocalDateTime.now(),
                path
        );
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String requestId, String path) {
        return fail(errorCode, errorCode.getDefaultMessage(), errorCode.getDefaultUserMessage(), requestId, path);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String errorMessage, String userMessage, String requestId, String path) {
        return new ApiResponse<>(
                errorCode.getHttpStatus(),
                errorCode.getCode(),
                errorMessage,
                userMessage,
                null,
                requestId,
                LocalDateTime.now(),
                path
        );
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

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
