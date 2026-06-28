package com.lumira.saas.modules.system.support;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ReadModelVersionCache;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class SmtpMailService {

    private static final String GLOBAL_CONFIG_CACHE_KEY = "global";
    private static final long CONFIG_CACHE_TTL_MS = 10 * 60_000L;
    private static final long READ_MODEL_VERSION_CACHE_TTL_MS = 2_000L;
    private static final String PLATFORM_SCOPE = "PLATFORM";
    private static final String READ_MODEL_CONTEXT_PLATFORM = "platform";
    private static final String READ_MODEL_SCOPE_RUNTIME_APPEARANCE = "runtime-appearance";
    private static final String READ_MODEL_CACHE_KEY = "smtp:platform/runtime-appearance";
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
    private final ReadModelVersionService readModelVersionService;
    private final ReadModelVersionCache readModelVersionCache;
    private final Cache<String, Map<String, String>> configSnapshotCache;
    private final Cache<String, CompletableFuture<Map<String, String>>> configLoadInFlight;

    public SmtpMailService(SysConfigMapper sysConfigMapper) {
        this(sysConfigMapper, null, new ReadModelVersionCache(READ_MODEL_VERSION_CACHE_TTL_MS));
    }

    @Autowired
    public SmtpMailService(
            SysConfigMapper sysConfigMapper,
            ReadModelVersionService readModelVersionService,
            ReadModelVersionCache readModelVersionCache
    ) {
        this.sysConfigMapper = sysConfigMapper;
        this.readModelVersionService = readModelVersionService;
        this.readModelVersionCache = readModelVersionCache;
        this.configSnapshotCache = CacheBuilder.newBuilder()
                .maximumSize(1024)
                .expireAfterWrite(CONFIG_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.configLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(1024)
                .expireAfterWrite(CONFIG_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    public boolean isConfigured() {
        Map<String, String> values = loadValues();
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

    public void sendVerificationCode(String toEmail, String verificationCode, String subjectPrefix) {
        sendPlainText(
                toEmail,
                StringUtils.hasText(subjectPrefix) ? subjectPrefix : "Email verification code",
                "Your verification code is " + verificationCode + ". It expires in 5 minutes."
        );
    }

    public void sendPlainText(String toEmail, String subject, String text) {
        Map<String, String> values = loadValues();
        JavaMailSenderImpl sender = buildSmtpSender(values);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(defaultIfBlank(values.get(SMTP_FROM_KEY), values.get(SMTP_USERNAME_KEY)));
        message.setSubject(StringUtils.hasText(subject) ? subject : "Lumira notification");
        message.setText(StringUtils.hasText(text) ? text : "");

        try {
            sender.send(message);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Failed to send email. Please check SMTP settings.");
        }
    }

    public void invalidate() {
        configSnapshotCache.invalidateAll();
        configLoadInFlight.invalidateAll();
        readModelVersionCache.invalidate(READ_MODEL_CACHE_KEY);
    }

    private Map<String, String> loadValues() {
        String cacheKey = cacheKey(currentRuntimeAppearanceVersion());
        Map<String, String> cached = configSnapshotCache.getIfPresent(cacheKey);
        if (cached != null) {
            return new HashMap<>(cached);
        }
        try {
            CompletableFuture<Map<String, String>> future = configLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.completedFuture(loadValuesFresh(cacheKey))
            );
            Map<String, String> values = future.join();
            configLoadInFlight.invalidate(cacheKey);
            return values;
        } catch (ExecutionException ex) {
            configLoadInFlight.invalidate(cacheKey);
            Throwable cause = ex.getCause();
            Map<String, String> cachedAfterFailure = configSnapshotCache.getIfPresent(cacheKey);
            if (cachedAfterFailure != null) {
                return new HashMap<>(cachedAfterFailure);
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load SMTP config", cause);
        } catch (RuntimeException ex) {
            configLoadInFlight.invalidate(cacheKey);
            Map<String, String> cachedAfterFailure = configSnapshotCache.getIfPresent(cacheKey);
            if (cachedAfterFailure != null) {
                return new HashMap<>(cachedAfterFailure);
            }
            throw ex;
        }
    }

    private Map<String, String> loadValuesFresh(String cacheKey) {
        Map<String, String> cached = configSnapshotCache.getIfPresent(cacheKey);
        if (cached != null) {
            return new HashMap<>(cached);
        }
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
        List<SysConfigEntity> rows = sysConfigMapper.listEffectiveValues(PLATFORM_SCOPE, keys);
        Map<String, String> values = new HashMap<>();
        for (SysConfigEntity row : rows) {
            String key = row.getConfigKey();
            String value = row.getConfigValue();
            if (key != null && value != null && !values.containsKey(key)) {
                values.put(key, value);
            }
        }
        configSnapshotCache.put(cacheKey, new HashMap<>(values));
        return values;
    }

    private String cacheKey(Long runtimeAppearanceVersion) {
        if (runtimeAppearanceVersion == null) {
            return GLOBAL_CONFIG_CACHE_KEY;
        }
        return "runtime-appearance:v" + runtimeAppearanceVersion;
    }

    private Long currentRuntimeAppearanceVersion() {
        if (readModelVersionService == null) {
            return null;
        }
        try {
            return readModelVersionCache.readValue(
                    READ_MODEL_CACHE_KEY,
                    READ_MODEL_VERSION_CACHE_TTL_MS,
                    () -> readModelVersionService.currentVersion(
                            READ_MODEL_CONTEXT_PLATFORM,
                            READ_MODEL_SCOPE_RUNTIME_APPEARANCE
                    )
            );
        } catch (RuntimeException exception) {
            return null;
        }
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
