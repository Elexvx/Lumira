package com.lumira.common.enums;

public enum ErrorCode {
    SUCCESS(200, "0", "成功", null),
    VALIDATION_ERROR(400, "A0400", "参数校验失败", "输入信息有误，请检查后重试"),
    UNAUTHORIZED(401, "A0401", "未登录", "请先登录后再继续操作"),
    LOGIN_FAILED(401, "A0402", "登录失败", "账号或密码错误"),
    FORBIDDEN(403, "A0403", "无权限", "当前账号没有访问权限"),
    NOT_FOUND(404, "A0404", "资源不存在", "请求的资源不存在或已被移除"),
    SESSION_EXPIRED(401, "A0405", "会话已失效", "登录状态已失效，请重新登录"),
    ACCOUNT_DISABLED(401, "A0406", "账号已禁用", "账号已被禁用，请联系管理员"),
    ACCOUNT_NOT_FOUND(401, "A0407", "账号不存在", "账号或密码错误"),
    PASSWORD_ERROR(401, "A0408", "密码错误", "账号或密码错误"),
    BAD_REQUEST(400, "A0409", "请求错误", "请检查请求内容后重试"),
    UNPROCESSABLE_ENTITY(422, "A0422", "参数无法处理", "输入信息有误，请检查后重试"),
    LOGIN_RATE_LIMITED(429, "B6001", "登录频率受限", "登录过于频繁，请稍后再试"),
    CAPTCHA_INVALID(400, "B6002", "验证码错误", "验证码错误或已过期，请刷新后重试"),
    REPEAT_SUBMIT(429, "B6003", "重复提交", "不允许重复提交，请稍后再试"),
    TRAFFIC_LIMITED(429, "B6004", "请求频率受限", "当前访问过于频繁，请稍后再试"),
    PASSWORD_POLICY_VIOLATION(400, "B3001", "密码不符合规范", "密码不符合安全要求"),
    BIZ_ERROR(409, "B0001", "业务异常", "当前操作无法完成，请检查业务状态"),
    PERMISSION_SNAPSHOT_ERROR(409, "P1001", "权限快照异常", "权限数据暂不可用，请稍后重试"),
    PLUGIN_PACKAGE_INVALID(400, "P2001", "插件包不合法", "插件包结构或元数据校验失败"),
    PLUGIN_SIGNATURE_INVALID(400, "P2002", "插件签名校验失败", "插件签名无效，请重新上传可信制品"),
    PLUGIN_CHECKSUM_INVALID(400, "P2003", "插件校验和校验失败", "插件文件已损坏，请重新上传"),
    PLUGIN_VERSION_INCOMPATIBLE(409, "P2004", "插件版本不兼容", "插件版本与当前平台不兼容"),
    PLUGIN_DEPENDENCY_CONFLICT(409, "P2005", "插件依赖冲突", "请先安装或启用依赖插件"),
    PLUGIN_RUNTIME_ERROR(500, "P2006", "插件运行时异常", "插件运行失败，请查看运行日志"),
    PLUGIN_NOT_ENABLED(403, "P2007", "插件未启用", "当前未启用该插件"),
    SYSTEM_ERROR(500, "S0001", "系统异常", "系统异常，请稍后重试");

    private final Integer httpStatus;
    private final String code;
    private final String defaultMessage;
    private final String defaultUserMessage;

    ErrorCode(Integer httpStatus, String code, String defaultMessage, String defaultUserMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.defaultUserMessage = defaultUserMessage;
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

    public String getDefaultUserMessage() {
        return defaultUserMessage;
    }
}
