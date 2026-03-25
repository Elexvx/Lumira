package com.yourcompany.saas.common.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yourcompany.saas.common.enums.ErrorCode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private String requestId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .requestId(requestId)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String requestId) {
        return ApiResponse.<T>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .requestId(requestId)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String requestId, String message) {
        ApiResponse<T> fail = fail(errorCode, requestId);
        fail.setMessage(message);
        return fail;
    }
}
