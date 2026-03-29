package com.yourcompany.saas.common.enums;

public enum ErrorCode {
    SUCCESS(200, "0", "成功", "请求已成功处理"),
    BAD_REQUEST(400, "A0400", "参数错误", "请检查输入内容后重试"),
    UNAUTHORIZED(401, "A0401", "未登录", "登录状态已失效，请重新登录"),
    FORBIDDEN(403, "A0403", "无权限", "当前账号没有访问权限"),
    NOT_FOUND(404, "A0404", "资源不存在", "请求的资源不存在或已被移除"),
    BIZ_ERROR(409, "B0001", "业务异常", "当前操作无法完成，请检查业务状态"),
    TENANT_ERROR(409, "T0001", "租户异常", "租户上下文无效，请切换后重试"),
    PERMISSION_SNAPSHOT_ERROR(409, "P1001", "权限快照异常", "权限数据暂不可用，请稍后重试"),
    PLUGIN_PACKAGE_INVALID(400, "P2001", "插件包不合法", "插件包结构或元数据校验失败"),
    PLUGIN_SIGNATURE_INVALID(400, "P2002", "插件签名校验失败", "插件签名无效，请重新上传可信制品"),
    PLUGIN_CHECKSUM_INVALID(400, "P2003", "插件校验和校验失败", "插件文件已损坏，请重新上传"),
    PLUGIN_VERSION_INCOMPATIBLE(409, "P2004", "插件版本不兼容", "插件版本与当前平台不兼容"),
    PLUGIN_DEPENDENCY_CONFLICT(409, "P2005", "插件依赖冲突", "请先安装或启用依赖插件"),
    PLUGIN_RUNTIME_ERROR(500, "P2006", "插件运行时异常", "插件运行失败，请查看运行日志"),
    PLUGIN_NOT_ENABLED(403, "P2007", "插件未启用", "当前租户未启用该插件"),
    SYSTEM_ERROR(500, "S0001", "系统异常", "系统繁忙，请稍后重试");

    private final Integer httpStatus;
    private final String code;
    private final String defaultMessage;
    private final String defaultUserTip;

    ErrorCode(Integer httpStatus, String code, String defaultMessage, String defaultUserTip) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.defaultUserTip = defaultUserTip;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public String getDefaultUserTip() {
        return defaultUserTip;
    }
}
