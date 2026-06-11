package com.lumira.saas.modules.system.support;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class SmtpMailService {

    private static final Long PLATFORM_TENANT_ID = com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    private static final String PLATFORM_SCOPE = "PLATFORM";
    private static final String SMTP_ENABLED_KEY = "smtp.enabled";
    private static final String SMTP_HOST_KEY = "smtp.host";
    private static final String SMTP_PORT_KEY = "smtp.port";
    private static final String SMTP_USERNAME_KEY = "smtp.username";
    private static final String SMTP_PASSWORD_KEY = "smtp.password";
    private static final String SMTP_FROM_KEY = "smtp.from";
    private static final String SMTP_AUTH_ENABLED_KEY = "smtp.auth-enabled";
    private static final String SMTP_STARTTLS_ENABLED_KEY = "smtp.starttls-enabled";
    private static final String SMTP_SSL_ENABLED_KEY = "smtp.ssl-enabled";

    private final SysConfigMapper sysConfigMapper;

    public SmtpMailService(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    public boolean isConfigured(Long tenantId) {
        Map<String, String> values = loadValues(tenantId);
        boolean enabled = Boolean.parseBoolean(defaultIfBlank(values.get(SMTP_ENABLED_KEY), "true"));
        String host = defaultIfBlank(values.get(SMTP_HOST_KEY), "");
        String from = defaultIfBlank(values.get(SMTP_FROM_KEY), "");
        String portValue = defaultIfBlank(values.get(SMTP_PORT_KEY), "0");
        int port;
        try {
            port = Integer.parseInt(portValue.trim());
        } catch (NumberFormatException ex) {
            port = 0;
        }
        boolean authEnabled = Boolean.parseBoolean(defaultIfBlank(values.get(SMTP_AUTH_ENABLED_KEY), "true"));
        boolean usernameRequired = authEnabled && !StringUtils.hasText(defaultIfBlank(values.get(SMTP_USERNAME_KEY), ""));
        return enabled && StringUtils.hasText(host) && port > 0 && StringUtils.hasText(from) && !usernameRequired;
    }

    public void sendVerificationCode(Long tenantId, String toEmail, String verificationCode, String subjectPrefix) {
        Map<String, String> values = loadValues(tenantId);
        JavaMailSenderImpl sender = buildSmtpSender(values);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(defaultIfBlank(values.get(SMTP_FROM_KEY), values.get(SMTP_USERNAME_KEY)));
        message.setSubject(StringUtils.hasText(subjectPrefix) ? subjectPrefix : "邮箱验证码");
        message.setText("您的验证码是 " + verificationCode + "，请在 5 分钟内完成验证。");

        try {
            sender.send(message);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "邮件发送失败，请检查 SMTP 配置");
        }
    }

    private Map<String, String> loadValues(Long tenantId) {
        Long effectiveTenantId = tenantId == null ? PLATFORM_TENANT_ID : tenantId;
        List<String> keys = List.of(
                SMTP_ENABLED_KEY,
                SMTP_HOST_KEY,
                SMTP_PORT_KEY,
                SMTP_USERNAME_KEY,
                SMTP_PASSWORD_KEY,
                SMTP_FROM_KEY,
                SMTP_AUTH_ENABLED_KEY,
                SMTP_STARTTLS_ENABLED_KEY,
                SMTP_SSL_ENABLED_KEY
        );
        List<SysConfigEntity> rows = sysConfigMapper.listEffectiveValues(effectiveTenantId, PLATFORM_SCOPE, keys);
        Map<String, String> values = new HashMap<>();
        for (SysConfigEntity row : rows) {
            String key = row.getConfigKey();
            String value = row.getConfigValue();
            if (key != null && value != null && !values.containsKey(key)) {
                values.put(key, value);
            }
        }
        return values;
    }

    private JavaMailSenderImpl buildSmtpSender(Map<String, String> values) {
        String host = defaultIfBlank(values.get(SMTP_HOST_KEY), "");
        int port = parseInt(defaultIfBlank(values.get(SMTP_PORT_KEY), "25"), 25);
        String username = defaultIfBlank(values.get(SMTP_USERNAME_KEY), "");
        String password = defaultIfBlank(values.get(SMTP_PASSWORD_KEY), "");

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
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
