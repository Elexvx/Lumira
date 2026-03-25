package com.yourcompany.saas.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS("0", "成功"),
    BAD_REQUEST("A0400", "参数错误"),
    UNAUTHORIZED("A0401", "未登录"),
    FORBIDDEN("A0403", "无权限"),
    NOT_FOUND("A0404", "资源不存在"),
    BIZ_ERROR("B0001", "业务异常"),
    TENANT_ERROR("T0001", "租户异常"),
    SYSTEM_ERROR("S0001", "系统异常");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
