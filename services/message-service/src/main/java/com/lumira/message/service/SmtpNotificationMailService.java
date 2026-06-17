package com.lumira.message.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class SmtpNotificationMailService {

    private static final String SMTP_HOST_KEY = "smtp.host";
    private static final String SMTP_PORT_KEY = "smtp.port";
    private static final String SMTP_USERNAME_KEY = "smtp.username";
    private static final String SMTP_PASSWORD_KEY = "smtp.password";
    private static final String SMTP_FROM_KEY = "smtp.from";
    private static final String SMTP_AUTH_ENABLED_KEY = "smtp.auth-enabled";
    private static final String SMTP_STARTTLS_ENABLED_KEY = "smtp.starttls-enabled";
    private static final String SMTP_SSL_ENABLED_KEY = "smtp.ssl-enabled";
    private static final List<String> SMTP_CONFIG_KEYS = List.of(
            SMTP_HOST_KEY,
            SMTP_PORT_KEY,
            SMTP_USERNAME_KEY,
            SMTP_PASSWORD_KEY,
            SMTP_FROM_KEY,
            SMTP_AUTH_ENABLED_KEY,
            SMTP_STARTTLS_ENABLED_KEY,
            SMTP_SSL_ENABLED_KEY
    );

    private final SystemInternalApi systemInternalApi;

    public SmtpNotificationMailService(SystemInternalApi systemInternalApi) {
        this.systemInternalApi = systemInternalApi;
    }

    public boolean isConfigured(Long tenantId) {
        Map<String, String> values = loadValues(tenantId);
        String host = defaultIfBlank(values.get(SMTP_HOST_KEY), "");
        String from = defaultIfBlank(values.get(SMTP_FROM_KEY), "");
        int port = parseInt(defaultIfBlank(values.get(SMTP_PORT_KEY), "0"), 0);
        boolean authEnabled = Boolean.parseBoolean(defaultIfBlank(values.get(SMTP_AUTH_ENABLED_KEY), "true"));
        boolean usernameMissing = authEnabled && !StringUtils.hasText(defaultIfBlank(values.get(SMTP_USERNAME_KEY), ""));
        return StringUtils.hasText(host) && port > 0 && StringUtils.hasText(from) && !usernameMissing;
    }

    public void send(Long tenantId, String toEmail, String subject, String content) {
        if (!StringUtils.hasText(toEmail)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "收件人邮箱不能为空");
        }
        Map<String, String> values = loadValues(tenantId);
        JavaMailSenderImpl sender = buildSmtpSender(values);
        String from = defaultIfBlank(values.get(SMTP_FROM_KEY), values.get(SMTP_USERNAME_KEY));
        if (!StringUtils.hasText(from)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充 SMTP 发件人地址");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(from);
        message.setSubject(defaultIfBlank(subject, "系统通知"));
        message.setText(defaultIfBlank(content, ""));
        sender.send(message);
    }

    private Map<String, String> loadValues(Long tenantId) {
        Long effectiveTenantId = tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        Map<String, String> values = systemInternalApi.platformConfigValues(effectiveTenantId, SMTP_CONFIG_KEYS);
        return values == null ? Map.of() : values;
    }

    private JavaMailSenderImpl buildSmtpSender(Map<String, String> values) {
        String host = defaultIfBlank(values.get(SMTP_HOST_KEY), "");
        int port = parseInt(defaultIfBlank(values.get(SMTP_PORT_KEY), "25"), 25);
        if (!StringUtils.hasText(host)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先配置 SMTP 主机");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(defaultIfBlank(values.get(SMTP_USERNAME_KEY), ""));
        sender.setPassword(defaultIfBlank(values.get(SMTP_PASSWORD_KEY), ""));
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(Boolean.parseBoolean(defaultIfBlank(values.get(SMTP_AUTH_ENABLED_KEY), "true"))));
        properties.put("mail.smtp.starttls.enable", String.valueOf(Boolean.parseBoolean(defaultIfBlank(values.get(SMTP_STARTTLS_ENABLED_KEY), "true"))));
        properties.put("mail.smtp.ssl.enable", String.valueOf(Boolean.parseBoolean(defaultIfBlank(values.get(SMTP_SSL_ENABLED_KEY), "false"))));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        return sender;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
